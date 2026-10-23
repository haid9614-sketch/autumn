package com.duong.autumn.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.duong.autumn.entity.ImageAsset;
import com.duong.autumn.entity.ImageAssetState;
import com.duong.autumn.repository.ImageAssetRepository;
import com.duong.autumn.repository.PostRepository;
import com.duong.autumn.repository.UserRepository;
import com.duong.autumn.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_FOLDERS = Set.of("autumn_posts", "autumn_avatars");

    private final Cloudinary cloudinary;
    private final ImageAssetRepository assetRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PlatformTransactionManager transactionManager;

    // Không giữ transaction DB trong suốt thời gian upload lên provider.
    public String uploadImage(MultipartFile file, String folderName) {
        Long ownerId = currentUserId();
        if (folderName == null || !ALLOWED_FOLDERS.contains(folderName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thư mục ảnh không hợp lệ.");
        }
        byte[] bytes = readImage(file);
        String publicId = folderName + "/" + ownerId + "/" + UUID.randomUUID();

        Map<?, ?> result;
        try {
            result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "public_id", publicId,
                    "resource_type", "image",
                    "overwrite", false,
                    "allowed_formats", List.of("jpg", "jpeg", "png", "webp", "gif")));
        } catch (IOException exception) {
            log.warn("Upload ảnh thất bại cho user {}", ownerId, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Không tải được ảnh lên dịch vụ lưu trữ. Hãy thử lại.");
        }

        try {
            if (result == null || !(result.get("secure_url") instanceof String imageUrl)
                    || !imageUrl.startsWith("https://") || imageUrl.length() > 255
                    || !publicId.equals(result.get("public_id"))) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Dịch vụ lưu trữ trả về thông tin ảnh không hợp lệ.");
            }
            assetRepository.save(ImageAsset.builder()
                    .ownerId(ownerId).publicId(publicId).imageUrl(imageUrl)
                    .folder(folderName).state(ImageAssetState.PENDING).build());
            return imageUrl;
        } catch (RuntimeException exception) {
            destroyBestEffort(publicId);
            throw exception;
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteImage(String imageUrl) {
        Long ownerId = currentUserId();
        DeletionTarget target = newTransaction().execute(status -> {
            ImageAsset asset = ownedAsset(imageUrl, ownerId);
            if (asset.getState() == ImageAssetState.DELETED) {
                return null;
            }
            if ((asset.getState() != ImageAssetState.PENDING
                    && asset.getState() != ImageAssetState.DELETE_PENDING) || isReferenced(imageUrl)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ảnh đang được sử dụng, không thể xóa qua API dọn ảnh tạm.");
            }
            asset.setState(ImageAssetState.DELETE_PENDING);
            assetRepository.save(asset);
            return targetOf(asset);
        });
        // Marker đã commit trước lời gọi provider. API trực tiếp vẫn báo 502 nếu provider lỗi.
        if (target != null) {
            finishDeletion(target);
        }
    }

    // Tham gia transaction tạo bài/đổi avatar; save thất bại thì trạng thái rollback.
    @Transactional(propagation = Propagation.MANDATORY)
    public void markImageInUse(String imageUrl, Long ownerId) {
        if (!Objects.equals(ownerId, currentUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không sở hữu ảnh này.");
        }
        ImageAsset asset = ownedAsset(imageUrl, ownerId);
        if (asset.getState() == ImageAssetState.DELETED
                || asset.getState() == ImageAssetState.DELETE_PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ảnh đã bị xóa hoặc đang chờ xóa. Hãy tải ảnh lại.");
        }
        if (asset.getState() == ImageAssetState.PENDING) {
            asset.setState(ImageAssetState.ATTACHED);
            assetRepository.save(asset);
        }
    }

    // Giữ tên cũ để PostService hiện tại tiếp tục gọi được.
    // Tham gia transaction xóa bài, hoặc tạo transaction ngắn khi dọn avatar đã commit.
    public void cleanupReplacedAvatar(String imageUrl, Long ownerId) {
        if (imageUrl == null || imageUrl.isBlank() || !Objects.equals(ownerId, currentUserId())) {
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ImageAsset asset = assetRepository.findOwnedForUpdate(imageUrl, ownerId).orElse(null);
            if (asset == null || asset.getState() == ImageAssetState.DELETED || isReferenced(imageUrl)) {
                return;
            }
            asset.setState(ImageAssetState.DELETE_PENDING);
            assetRepository.save(asset);
            DeletionTarget target = targetOf(asset);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    finishDeletionBestEffort(target);
                }
            });
        });
    }

    // Job gọi không cần SecurityContext; danh sách chỉ lấy marker do backend đã tạo.
    public void retryPendingDeletions() {
        for (ImageAsset asset : assetRepository.findTop50ByStateOrderByIdAsc(ImageAssetState.DELETE_PENDING)) {
            finishDeletionBestEffort(targetOf(asset));
        }
    }

    private record DeletionTarget(String imageUrl, Long ownerId, String publicId) {}

    private DeletionTarget targetOf(ImageAsset asset) {
        return new DeletionTarget(asset.getImageUrl(), asset.getOwnerId(), asset.getPublicId());
    }

    private boolean isReferenced(String imageUrl) {
        return postRepository.existsByImage(imageUrl) || userRepository.existsByAvatar(imageUrl);
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private void finishDeletionBestEffort(DeletionTarget target) {
        try {
            finishDeletion(target);
        } catch (RuntimeException exception) {
            // Giữ DELETE_PENDING trong DB; job thử lại cả khi app đã khởi động lại.
            log.warn("Chưa dọn được ảnh {}; sẽ thử lại", target.publicId(), exception);
        }
    }

    private void finishDeletion(DeletionTarget target) {
        DeletionTarget ready = newTransaction().execute(status -> {
            ImageAsset asset = assetRepository.findOwnedForUpdate(target.imageUrl(), target.ownerId()).orElse(null);
            if (asset == null || asset.getState() != ImageAssetState.DELETE_PENDING) {
                return null;
            }
            if (isReferenced(asset.getImageUrl())) {
                // Bảo vệ dữ liệu cũ không nhất quán; không xóa ảnh vẫn còn tham chiếu.
                asset.setState(ImageAssetState.ATTACHED);
                assetRepository.save(asset);
                return null;
            }
            return targetOf(asset);
        });
        if (ready == null) {
            return;
        }

        // Không giữ transaction kiểm tra trong lúc gọi mạng.
        // DELETE_PENDING ngăn đăng bài/đổi avatar gắn lại ảnh trong khoảng này.
        destroy(ready.publicId());
        newTransaction().executeWithoutResult(status -> {
            ImageAsset asset = assetRepository.findOwnedForUpdate(ready.imageUrl(), ready.ownerId()).orElse(null);
            if (asset != null && asset.getState() == ImageAssetState.DELETE_PENDING) {
                asset.setState(ImageAssetState.DELETED);
                assetRepository.save(asset);
            }
        });
    }

    private ImageAsset ownedAsset(String imageUrl, Long ownerId) {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đường dẫn ảnh không hợp lệ.");
        }
        return assetRepository.findOwnedForUpdate(imageUrl, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy ảnh thuộc tài khoản của bạn. Hãy tải ảnh qua API trước."));
    }

    private byte[] readImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File ảnh không được để trống.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh không được vượt quá 10 MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đọc được file ảnh.");
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh không được vượt quá 10 MB.");
        }
        if (bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dung lượng ảnh không hợp lệ.");
        }
        boolean jpeg = bytes.length >= 3 && (bytes[0] & 255) == 255
                && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
        boolean png = bytes.length >= 8 && (bytes[0] & 255) == 137
                && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71
                && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
        boolean gif = bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
        boolean webp = bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W'
                && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        if (!jpeg && !png && !gif && !webp) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chỉ hỗ trợ ảnh JPEG, PNG, GIF hoặc WebP.");
        }
        // Provider resource_type=image còn giải mã và kiểm tra nội dung ảnh thật.
        return bytes;
    }

    private void destroy(String publicId) {
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId,
                    ObjectUtils.asMap("resource_type", "image", "invalidate", true));
            Object outcome = result == null ? null : result.get("result");
            if (!"ok".equals(outcome) && !"not found".equals(outcome)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Dịch vụ lưu trữ chưa xác nhận xóa ảnh. Hãy thử lại.");
            }
        } catch (IOException exception) {
            log.warn("Xóa ảnh thất bại cho publicId {}", publicId, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Không xóa được ảnh trên dịch vụ lưu trữ. Hãy thử lại.");
        }
    }

    private void destroyBestEffort(String publicId) {
        try {
            destroy(publicId);
        } catch (RuntimeException exception) {
            log.warn("Cần dọn lại ảnh chưa lưu được metadata: {}", publicId, exception);
        }
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails details)
                || details.getId() == null || !details.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập.");
        }
        return details.getId();
    }


}

