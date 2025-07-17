package com.dbbackup.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/backup")
    public String backup(){
        return "backup";
    }

    @GetMapping("/restore")
    public String restore() {
        return "restore";
    }
}
