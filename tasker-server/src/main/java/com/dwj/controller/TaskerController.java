package com.dwj.controller;

import com.dwj.resource.SysProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * @author daiwj
 * @date 2021/05/30
 * @description:
 */
@Controller
@RequestMapping("/tasker")
public class TaskerController {

    @RequestMapping("/get")
    public void getConfig(@RequestParam String name, HttpServletResponse response){
//        return SysProperties.getConfigMap(name);
        try {
            String path = "C:\\Users\\28934\\Desktop\\classes";
            response.setCharacterEncoding("UTF-8");
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            PrintWriter writer = response.getWriter();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(new File(path, name))))){
                while (true){
                    String line = in.readLine();
                    if(line == null) break;
                    writer.println(line);
                }
            }
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
