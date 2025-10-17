package tech.artcoded.websitev2.pages.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/cache")
public class CacheController {
    private final CacheManager cacheManager;

    @Inject
    public CacheController(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @PostMapping("/find-all")
    public Collection<String> findAll() {
        return cacheManager.getCacheNames();
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Map.Entry<String, String>> clear(@RequestParam("name") String cacheName) {
        Optional.ofNullable(this.cacheManager.getCache(cacheName)).ifPresent(Cache::clear);
        return ResponseEntity.ok(Map.entry("message", "cache cleared"));
    }

}
