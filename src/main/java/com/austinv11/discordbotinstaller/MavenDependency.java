package com.austinv11.discordbotinstaller;

public class MavenDependency {
	
	public final String groupId;
	public final String artifactId;
	public final String version;
	public final String identifier;
	
	public MavenDependency(String groupId, String artifactId, String version, String identifier) {
		this.artifactId = artifactId;
		this.groupId = groupId;
		this.version = version;
		this.identifier = identifier;
	}
	
	public MavenDependency(String groupId, String artifactId, String version) {
		this.artifactId = artifactId;
		this.groupId = groupId;
		this.version = version;
		this.identifier = null;
	}
	
	public String formUrl() {
		String url = "";
		url += groupId.replace(".", "/");
		url += "/"+artifactId;
		url += "/"+version;
		url += "/"+artifactId+"-"+version;
		if (identifier != null)
			url += "-"+identifier;
		url += ".jar";
		return url;
	}
}
