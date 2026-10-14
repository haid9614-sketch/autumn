package com.duong.autumn.controller;

import com.duong.autumn.dto.response.FeedResponse;
import com.duong.autumn.feed.FeedType;
import com.duong.autumn.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "feedSessionId", required = false) String feedSessionId) {
        return postService.getFeed(FeedType.parse(type), cursor, limit, feedSessionId);
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeSession(@PathVariable("id") String id) {
        postService.closeFeedSession(id);
    }
}
