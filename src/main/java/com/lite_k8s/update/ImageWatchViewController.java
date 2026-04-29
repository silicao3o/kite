package com.lite_k8s.update;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ImageWatchViewController {

    @GetMapping("/image-watches")
    public String imageWatches() {
        return "image-watches";
    }

    @GetMapping("/deploy-map")
    public String deployMap() {
        return "deploy-map";
    }
}
