package com.duong.autumn.dto.response;

/** id dùng để mở profile; ba trường còn lại hiển thị trên danh sách tìm kiếm. */
public record UserSearchResponse(Long id, String avatar, String name, String username) {
}

