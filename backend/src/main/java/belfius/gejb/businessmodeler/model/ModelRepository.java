package belfius.gejb.businessmodeler.model;

import lombok.Data;

import java.util.List;

@Data
public class ModelRepository {
    private String name;
    private String projectCode;
    private String path;
    private String type;
    private String username;
    private String password;
    private String mainBranch;
    private List branchList;
    private String remoteUrl;
    private String defaultCommitUser = "Business Modeler Admin";
}
