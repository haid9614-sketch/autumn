package com.duong.autumn.controller;

import com.duong.autumn.dto.response.FeedResponse;
import com.duong.autumn.feed.FeedType;
import com.duong.autumn.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
public class FeedController {
    private final PostService postService;

    @GetMapping("/{type}")
    public FeedResponse getFeed(
            @PathVariable("type") String type,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return postService.getFeed(FeedType.parse(type), cursor, limit);
    }
}
