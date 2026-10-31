package com.reader.file;

import com.reader.common.ApiResponse;
import com.reader.common.BusinessException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件接口控制器：秒传预检、文件上传。
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024; // 100 MB

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 秒传预检：客户端上传前先传 SHA-256，判断文件是否已存在。
     */
    @PostMapping("/check")
    public ApiResponse<UploadCheckResponse> check(@RequestParam("sha256") String sha256) {
        if (sha256 == null || sha256.length() != 64) {
            throw new BusinessException(400, "无效的 SHA-256 指纹");
        }
        return ApiResponse.ok(fileService.check(sha256));
    }

    /**
     * 上传文件：分片/整文件由前端处理，此处接收文件字节与 SHA-256、类型。
     */
    @PostMapping("/upload")
    public ApiResponse<FileAsset> upload(@RequestParam("sha256") String sha256,
                                         @RequestParam("fileType") int fileType,
                                         @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(400, "文件大小超过 100 MB 上限");
        }
        if (fileType != 1 && fileType != 2) {
            throw new BusinessException(400, "文件类型非法（1=EPUB, 2=TXT）");
        }

        byte[] content = file.getBytes();
        String actualSha256 = FileService.sha256(content);
        if (!actualSha256.equals(sha256)) {
            throw new BusinessException(400, "SHA-256 校验不一致");
        }

        FileAsset asset = fileService.upload(sha256, content, fileType, file.getContentType(),
                file.getOriginalFilename());
        return ApiResponse.ok(asset);
    }
}
