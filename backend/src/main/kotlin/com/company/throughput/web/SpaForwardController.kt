package com.company.throughput.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class SpaForwardController {
    @GetMapping(
        value = [
            "/sync",
            "/explorer",
            "/explorer/**",
            "/activity",
            "/activity/**",
            "/codebase",
            "/codebase/**",
            "/contributors",
            "/widgets",
            "/widgets/**",
            "/pages/**",
            "/settings/**",
            "/legacy/**",
        ],
    )
    fun forward(@Suppress("UNUSED_PARAMETER") request: HttpServletRequest): String {
        return "forward:/index.html"
    }
}
