package com.austinv11.discordbotinstaller;

import com.google.gson.Gson;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DiscordBotInstaller {
	
	public static ErrorCodes error = ErrorCodes.OK;
	public static OS os;
	public static List<String> mavenRepos = new ArrayList<String>();
	public static List<MavenDependency> mavenDependencies = new ArrayList<MavenDependency>();
	public static List<DirectDependency> directDependencies = new ArrayList<DirectDependency>();
	public static AssetResponse release;
	
	public static void main(String[] args) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.err.println("Installer exited with status "+error.name()+" (status code "+error.ordinal()+")");
			}
		});
		
		try {
			System.out.println("Running system requirements check...");
			testJavaVersion();
			testOS();
			
			System.out.println("Checks passed. Retrieving dependency information...");
			retrieveDependencyInfo();
			System.out.println("A total of "+(mavenDependencies.size()+directDependencies.size())+" dependencies found");
			
			System.out.println("Retrieving latest release information...");
			retrieveReleaseInfo();
			
			System.out.println("Preparing system for installation...");
			prepareForInstall();
			
			System.out.println("Installing...");
			install();
			
			System.out.println("Done! Please refer to the DiscordBot wiki for information on how to use the bot.");
		} catch (GeneralException e) {
			error = e.code;
		} catch (Exception e) {
			e.printStackTrace();
			error = ErrorCodes.UNKNOWN_EXCEPTION;
		}
	}
	
	private static void testJavaVersion() throws GeneralException {
		String version = System.getProperty("java.version");
		int pos = version.indexOf('.');
		pos = version.indexOf('.', pos+1);
		double javaVersion = Double.parseDouble(version.substring(0, pos));
		if (javaVersion < 1.8)
			throw new GeneralException(ErrorCodes.BAD_JAVA_VERSION);
	}
	
	private static void testOS() throws GeneralException {
		String property = System.getProperty("os.name").toLowerCase();
		if (property.contains("mac") || property.contains("darwin")) {
			os = OS.MAC;
		} else if (property.contains("win")) {
			os = OS.WINDOWS;
		} else if (property.contains("nux")) {
			os = OS.LINUX;
		} else {
			throw new GeneralException(ErrorCodes.UNKNOWN_OS);
		}
	}
	
	private static void retrieveDependencyInfo() throws GeneralException {
		try {
			URL mvnReposUrl = new URL(Constants.REPO_URL+Constants.REPO_LIST_URI);
			HttpURLConnection reposConnection = (HttpURLConnection) mvnReposUrl.openConnection();
			BufferedReader reader = new BufferedReader(new InputStreamReader(reposConnection.getInputStream()));
			String currentLine;
			while ((currentLine = reader.readLine()) != null) {
				if (currentLine.startsWith("#"))
					continue;
				mavenRepos.add(currentLine);
			}
			reader.close();
			
			URL mvnDependenciesUrl = new URL(Constants.REPO_URL+Constants.DEPENDENCIES_LIST_URI);
			HttpURLConnection dependenciesConnection = (HttpURLConnection) mvnDependenciesUrl.openConnection();
			reader = new BufferedReader(new InputStreamReader(dependenciesConnection.getInputStream()));
			while ((currentLine = reader.readLine()) != null) {
				if (currentLine.startsWith("#"))
					continue;
				String[] info = currentLine.split(":");
				if (info.length == 3) {
					mavenDependencies.add(new MavenDependency(info[0], info[1], info[2]));
				} else {
					mavenDependencies.add(new MavenDependency(info[0], info[1], info[2], info[3]));
				}
			}
			reader.close();
			
			URL directDependenciesUrl = new URL(Constants.REPO_URL+Constants.LIB_INDEX_URI);
			HttpURLConnection directDependenciesConnection = (HttpURLConnection) directDependenciesUrl.openConnection();
			reader = new BufferedReader(new InputStreamReader(directDependenciesConnection.getInputStream()));
			while ((currentLine = reader.readLine()) != null) {
				if (currentLine.startsWith("#") || Constants.LIB_INDEX_URI.contains(currentLine))
					continue;
				directDependencies.add(new DirectDependency(Constants.MEDIA_REPO_URL+Constants.LIBS_DIR_URI+"/"+currentLine, currentLine));
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
			throw new GeneralException(ErrorCodes.CONNECTION_ERROR);
		}
	}
	
	private static void retrieveReleaseInfo() throws GeneralException {
		try {
			URL releaseUrl = new URL(Constants.API_REPO_URL+Constants.LATEST_RELEASE_URI);
			HttpURLConnection connection = (HttpURLConnection) releaseUrl.openConnection();
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			Gson gson = new Gson();
			ReleasesResponse response = gson.fromJson(reader, ReleasesResponse.class);
			release = response.assets[0];
		} catch (IOException e) {
			e.printStackTrace();
			throw new GeneralException(ErrorCodes.CONNECTION_ERROR);
		}
	}
	
	private static void prepareForInstall() throws GeneralException {
		File filelist = new File("./installed.txt");
		if (filelist.exists()) {
			try {
				BufferedReader reader = new BufferedReader(new FileReader(filelist));
				String currentLine;
				while ((currentLine = reader.readLine()) != null) {
					if (currentLine.startsWith("#") || currentLine.isEmpty())
						continue;
					File file = new File(currentLine);
					if (file.exists())
						file.delete();
				}
				reader.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace(); //Should never be reached
			} catch (IOException e) {
				e.printStackTrace();
				throw new GeneralException(ErrorCodes.FILE_READ_ERROR);
			}
			
		}
	}
	
	private static void install() throws GeneralException {
		List<String> installedList = new ArrayList<String>();
		int dependencyCounter = 1;
		int maxDependencies = mavenDependencies.size()+directDependencies.size();
		
		for (MavenDependency dependency : mavenDependencies) {
			String fileName = dependency.artifactId+"-"+dependency.version+
					(dependency.identifier == null ? "" : "-"+dependency.identifier)+".jar";
			System.out.println("Installing dependency "+fileName+" ("+dependencyCounter+"/"+maxDependencies+")");
			System.out.print("Resolving url...");
			String link = findMavenLink(dependency);
			System.out.print("Downloading...");
			fileName = "./"+fileName;
			download(link, fileName);
			installedList.add(fileName);
			dependencyCounter++;
			System.out.println("Done!");
		}
		
		for (DirectDependency dependency : directDependencies) {
			System.out.println("Installing dependency "+dependency.name+" ("+dependencyCounter+"/"+maxDependencies+")");
			System.out.print("Downloading...");
			String fileName = "./"+dependency.name;
			download(dependency.url, fileName);
			installedList.add(fileName);
			dependencyCounter++;
			System.out.println("Done!");
		}
		
		System.out.println("Installing DiscordBot");
		System.out.print("Downloading...");
		String fileName = "./"+release.name;
		download(release.browser_download_url, fileName);
		installedList.add(fileName);
		System.out.println("Done!");
		
		System.out.println("Finishing installation...");
		try {
			PrintWriter writer = new PrintWriter(new FileWriter("./installed.txt"));
			writer.println("# This is used to determine what files to manage when updating the bot");
			for (String file : installedList)
				writer.println(file);
			writer.close();
			
			String script;//FIXME: Fix the scripts
			if (os == OS.WINDOWS) {
				script = getTextFromClasspath("win.bat");
			} else {
				script = getTextFromClasspath("unix.sh");
			}
			script.replaceAll("@JAR_FILE@", release.name);
			String scriptName = "./";
			switch (os) {
				case WINDOWS:
					scriptName += "RUN_BOT.bat";
					break;
				case MAC:
					scriptName += "RUN_BOT.command";
					break;
				case LINUX:
					scriptName += "RUN_BOT.sh";
			}
			writer = new PrintWriter(new FileWriter(scriptName));
			writer.print(script);
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
			throw new GeneralException(ErrorCodes.FILE_WRITE_ERROR);
		}
	}
	
	private static String getTextFromClasspath(String filepath) throws GeneralException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(DiscordBotInstaller.class.getClassLoader().getResourceAsStream(filepath)));
		String fileText = "";
		String line;
		try {
			while ((line = reader.readLine()) != null)
				fileText += line+"\n";
			reader.close();
			return fileText;
		} catch (IOException e) {
			e.printStackTrace();
			throw new GeneralException(ErrorCodes.FILE_READ_ERROR);
		}
	}
	
	private static String findMavenLink(MavenDependency dependency) throws GeneralException {
		return findMavenLink(dependency, 0);
	}
	
	private static String findMavenLink(MavenDependency dependency, int depth) throws GeneralException {
		if (depth >= mavenRepos.size())
			throw new GeneralException(ErrorCodes.UNRESOLVED_DEPENDENCY);
		String repo = mavenRepos.get(depth);
		try {
			URL repoUrl = new URL(repo+dependency.formUrl());
			repoUrl.openConnection().getInputStream().close();
			return repo+dependency.formUrl();
		} catch (IOException e) {
			return findMavenLink(dependency, depth+1);
		}
	}
	
	private static void download(String link, String fileName) throws GeneralException {
		try {
			URL url = new URL(link);
			HttpURLConnection http = (HttpURLConnection) url.openConnection();
			Map<String, List<String>> header = http.getHeaderFields();
			while (isRedirected(header)) {
				link = header.get("Location").get(0);
				url = new URL(link);
				http = (HttpURLConnection) url.openConnection();
				header = http.getHeaderFields();
			}
			InputStream input = http.getInputStream();
			byte[] buffer = new byte[4096];
			int n = -1;
			OutputStream output = new FileOutputStream(new File(fileName));
			while ((n = input.read(buffer)) != -1) {
				output.write(buffer, 0, n);
			}
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
			throw new GeneralException(ErrorCodes.CONNECTION_ERROR);
		}
	}
	
	private static boolean isRedirected(Map<String,List<String>> header ) {
		for (String hv : header.get(null)) {
			if (hv.contains(" 301 ") || hv.contains(" 302 "))
				return true;
		}
		return false;
	}
	
	//Stripped responses, using only the fields needed
	private static class ReleasesResponse {
		public AssetResponse[] assets;
	}
	
	private static class AssetResponse {
		public String browser_download_url;
		public String name;
	}
}
