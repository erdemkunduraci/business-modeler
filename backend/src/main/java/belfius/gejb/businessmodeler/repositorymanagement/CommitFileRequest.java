package belfius.gejb.businessmodeler.repositorymanagement;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CommitFileRequest {
    private MultipartFile file;
    private String content;
    private String branch;
    private String commitMessage;
    private String authorName;
    private String authorEmail;
}
