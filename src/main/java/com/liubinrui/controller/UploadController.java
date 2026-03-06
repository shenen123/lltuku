package com.liubinrui.controller;

import com.liubinrui.common.BaseResponse;
import com.liubinrui.common.ResultUtils;
import com.liubinrui.utils.MinioUtil;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/upload")
public class UploadController {

    @PostMapping("/file")
    public BaseResponse<String> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        String fileName = "uploads/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        String contentType = file.getContentType();

        MinioUtil.upload(fileName, contentType, file.getInputStream());
        return ResultUtils.success("ok");
    }

}
