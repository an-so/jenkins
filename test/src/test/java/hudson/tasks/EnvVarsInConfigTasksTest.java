package hudson.tasks;


import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.JDK;
import hudson.model.Result;
import hudson.model.labels.LabelAtom;
import hudson.slaves.DumbSlave;
import hudson.tasks.Maven.MavenInstallation;
import org.apache.tools.ant.taskdefs.condition.Os;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.ToolInstallations;

public class EnvVarsInConfigTasksTest {
	public static final String DUMMY_LOCATION_VARNAME = "TOOLS_DUMMY_LOCATION";

	private DumbSlave agentEnv = null;
	private DumbSlave agentRegular = null;

	@ClassRule
	public static BuildWatcher buildWatcher = new BuildWatcher();

	@Rule
	public JenkinsRule j = new JenkinsRule();

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Before
	public void setUp() throws Exception {
		JDK defaultJDK = j.jenkins.getJDK(null);
		JDK varJDK = new JDK("varJDK", withVariable(defaultJDK.getHome()));
		j.jenkins.getJDKs().add(varJDK);

		// Maven with a variable in its path
		ToolInstallations.configureDefaultMaven();
		MavenInstallation defaultMaven = j.jenkins.getDescriptorByType(Maven.DescriptorImpl.class).getInstallations()[0];
		MavenInstallation varMaven = new MavenInstallation("varMaven",
				withVariable(defaultMaven.getHome()), JenkinsRule.NO_PROPERTIES);
		j.jenkins.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(varMaven);

		// create agents
		EnvVars additionalEnv = new EnvVars(DUMMY_LOCATION_VARNAME, "");
		agentEnv = j.createSlave(new LabelAtom("agentEnv"), additionalEnv);
		agentRegular = j.createSlave(new LabelAtom("agentRegular"));
	}

	private String withVariable(String s) {
		return s + "${" + DUMMY_LOCATION_VARNAME + "}";
	}

	@Test
	public void testFreeStyleShellOnAgent() throws Exception {
		FreeStyleProject project = j.createFreeStyleProject();
		if (Os.isFamily("dos")) {
			project.getBuildersList().add(new BatchFile("echo %JAVA_HOME%"));
		} else {
			project.getBuildersList().add(new Shell("echo \"$JAVA_HOME\""));
		}
		project.setJDK(j.jenkins.getJDK("varJDK"));

		// set appropriate SCM to get the necessary build files
		project.setScm(new ExtractResourceSCM(getClass().getResource(
				"/simple-projects.zip")));

		// test the regular agent - variable not expanded
		project.setAssignedLabel(agentRegular.getSelfLabel());
		FreeStyleBuild build = project.scheduleBuild2(0).get();

		j.assertBuildStatusSuccess(build);

		j.assertLogContains(DUMMY_LOCATION_VARNAME, build);

		// test the agent with prepared environment
		project.setAssignedLabel(agentEnv.getSelfLabel());
		build = project.scheduleBuild2(0).get();

		j.assertBuildStatusSuccess(build);

		// Check variable was expanded
		j.assertLogNotContains(DUMMY_LOCATION_VARNAME, build);
	}

	@Test
	public void testFreeStyleMavenOnAgent() throws Exception {
		FreeStyleProject project = j.createFreeStyleProject();
		project.setJDK(j.jenkins.getJDK("varJDK"));
		project.setScm(new ExtractResourceSCM(getClass().getResource(
				"/simple-projects.zip")));

		project.getBuildersList().add(
					      new Maven("test", "varMaven", "pom.xml${"
							+ DUMMY_LOCATION_VARNAME + "}", "", "",
							false));

		// test the regular agent - variable not expanded
		project.setAssignedLabel(agentRegular.getSelfLabel());
		FreeStyleBuild build = project.scheduleBuild2(0).get();

		j.assertBuildStatus(Result.FAILURE, build);

		j.assertLogContains(DUMMY_LOCATION_VARNAME, build);

		// test the agent with prepared environment
		project.setAssignedLabel(agentEnv.getSelfLabel());
		build = project.scheduleBuild2(0).get();

		j.assertBuildStatusSuccess(build);

		// Check variable was expanded
		j.assertLogNotContains(DUMMY_LOCATION_VARNAME, build);
	}
}
