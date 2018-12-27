package org.jfrog.hudson.pipeline.common.executors;

import com.google.common.collect.ArrayListMultimap;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Cause;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.BuildInfoFields;
import org.jfrog.build.client.ProxyConfiguration;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.generic.GenericArtifactsDeployer;
import org.jfrog.hudson.pipeline.common.Utils;
import org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfo;
import org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfoAccessor;
import org.jfrog.hudson.util.BuildUniqueIdentifierHelper;
import org.jfrog.hudson.util.Credentials;
import org.jfrog.hudson.util.ExtractorUtils;

import java.io.IOException;
import java.util.List;

/**
 * Created by romang on 4/24/16.
 */
public class GenericUploadExecutor implements Executor {
    private transient FilePath ws;
    private transient Run build;
    private transient TaskListener listener;
    private BuildInfo buildInfo;
    private boolean failNoOp;
    private ArtifactoryServer server;
    private StepContext context;
    private String spec;

    public GenericUploadExecutor(ArtifactoryServer server, TaskListener listener, Run build, FilePath ws, BuildInfo buildInfo, StepContext context, String spec, boolean failNoOp) {
        this.server = server;
        this.listener = listener;
        this.build = build;
        this.buildInfo = Utils.prepareBuildinfo(build, buildInfo);
        this.ws = ws;
        this.context = context;
        this.spec = spec;
        this.failNoOp = failNoOp;
    }

    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    public void execute() throws IOException, InterruptedException {
        Credentials credentials = new Credentials(server.getDeployerCredentialsConfig().provideUsername(build.getParent()),
                server.getDeployerCredentialsConfig().providePassword(build.getParent()));
        ProxyConfiguration proxyConfiguration = Utils.getProxyConfiguration(server);
        List<Artifact> deployedArtifacts = ws.act(new GenericArtifactsDeployer.FilesDeployerCallable(listener, spec,
                server, credentials, getPropertiesMap(), proxyConfiguration));
        if (failNoOp && deployedArtifacts.isEmpty()) {
            throw new RuntimeException("Fail-no-op: No files were affected in the upload process.");
        }
        new BuildInfoAccessor(buildInfo).appendDeployedArtifacts(deployedArtifacts);
    }

    private ArrayListMultimap<String, String> getPropertiesMap() throws IOException, InterruptedException {
        ArrayListMultimap<String, String> properties = ArrayListMultimap.create();

        if (buildInfo.getName() != null) {
            properties.put("build.name", buildInfo.getName());
        } else {
            properties.put("build.name", BuildUniqueIdentifierHelper.getBuildName(build));
        }
        if (buildInfo.getNumber() != null) {
            properties.put("build.number", buildInfo.getNumber());
        } else {
            properties.put("build.number", BuildUniqueIdentifierHelper.getBuildNumber(build));
        }
        properties.put("build.timestamp", build.getTimestamp().getTime().getTime() + "");
        Cause.UpstreamCause parent = ActionableHelper.getUpstreamCause(build);
        if (parent != null) {
            properties.put("build.parentName", ExtractorUtils.sanitizeBuildName(parent.getUpstreamProject()));
            properties.put("build.parentNumber", parent.getUpstreamBuild() + "");
        }
        EnvVars env = context.get(EnvVars.class);
        String revision = ExtractorUtils.getVcsRevision(env);
        if (StringUtils.isNotBlank(revision)) {
            properties.put(BuildInfoFields.VCS_REVISION, revision);
        }
        return properties;
    }
}