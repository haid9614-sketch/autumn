package com.duong.autumn.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final Cloudinary cloudinary;

    public String uploadImage(MultipartFile file, String folderName) {
        try {
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("folder", folderName)
            );
            return uploadResult.get("secure_url").toString();
        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi tải ảnh lên hệ thống đám mây");
        }
    }

    // hàm xóa ảnh
    public void deleteImage(String imageUrl) {
        try {
            // Cắt lấy public_id từ URL
            // https://.../v1234/autumn_posts/abc.jpg -> autumn_posts/abc
            int startIndex = imageUrl.indexOf("autumn_");
            int endIndex = imageUrl.lastIndexOf(".");

            if (startIndex == -1 || endIndex == -1) {
                throw new RuntimeException("Đường dẫn ảnh không hợp lệ");
            }

            String publicId = imageUrl.substring(startIndex, endIndex);

            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi xóa ảnh trên hệ thống đám mây");
        }
    }


}
