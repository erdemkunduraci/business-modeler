package belfius.gejb.businessmodeler.repositorymanagement;

import lombok.Data;

@Data
public class CreateBranchRequest {
    private String branchName;
    private String sourceBranch;
}
