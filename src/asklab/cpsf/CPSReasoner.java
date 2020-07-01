package asklab.cpsf;

import java.io.*;
import java.util.List;
import java.util.Arrays;
import java.util.Random;
import java.util.Vector;

public class CPSReasoner {
	static final String versionFile = "./version.txt";

	/* Available ASP solvers */
	public static final int SLVR_CLINGO = 1;
	public static final int SLVR_DLV = 2;
	public static final int SLVR_CLINGO_ALL = 3;
	public static final int SLVR_DLV_ALL = 4;

	public static String version() {
		String vFile, version;

		try {
			version = readFile(pkgPath(versionFile));
		} catch (Exception x) {
			System.out.println("Exception while loading file " + versionFile + ": " + x);
			x.printStackTrace();
			version = "[Specify in " + versionFile + " in the CPSReasoner folder]";
		}
		return (version);
	}

	public static String query(String sparqlQ, String aspQ, String ontologyDir) {
		return (query(sparqlQ, aspQ, ontologyDir, SLVR_DLV));
	}

	public static String query(String sparqlQ, String aspQ, String ontologyDir, int solver) {
		String tmpFile = "./tmpfile.sparql";
		String tmpFileASP = "./tmpfileASP.sparql";
		String query = sparqlQ;
		String asp = aspQ;
		String res = "";

		System.out.println("query=" + query);

		try {
			String cmd;

			writeToFile(tmpFile, query);

			if (asp.equals("")) {
				cmd = jenaCmd(tmpFile, ontologyDir);
				res = cmdOutToString(runCmdRaw(cmd));

				return (res);
			}

			String out;
			String aspProg;
			Vector<String> lines;

			cmd = jenaCmd(tmpFile, ontologyDir);
			lines = runCmdRaw(cmd);

			/* map jena's output to ASP facts */
			aspProg = jenaToASP(lines);
			/* add the query */
			aspProg += asp;
			System.out.println("asp=" + aspProg);

			/* run the ASP query */
			writeToFile(tmpFileASP, aspProg);
			switch (solver) {
			case SLVR_DLV_ALL:
				cmd = dlvCmdAll(tmpFileASP);
				break;
			case SLVR_CLINGO_ALL:
				cmd = clingoCmdAll(tmpFileASP);
				break;
			case SLVR_DLV:
				cmd = dlvCmd(tmpFileASP);
				break;
			case SLVR_CLINGO:
				cmd = clingoCmd(tmpFileASP);
				break;
			}
			out = runCmd(cmd);

			//System.out.println("---ThanhNH : " + out);
			/* Write Raw output to file using to export CSV */
			writeToFile("./Integration/ASP/tmpASPoutput.txt",out);

			/* convert output to one-literal-per-line */
			cmd = mkatomsCmd();
			lines = runCmdRaw(cmd, out);

		    //System.out.println(lines);

			/* convert the output to OWL reasoner style */
			Vector<Vector<String>> models;
			models = modelEnumerator.models(lines);
			res = "";
			for (Vector<String> lines2 : models) {
				aspToOWLoutput a = new aspToOWLoutput();
				if (!res.equals(""))
					res = res + "\n";
				//System.out.println(lines2);
				res += a.process(lines2);
			}
			// System.out.println(res);
		} catch (FileNotFoundException fnx) {
			System.out.println("Unable to create file " + tmpFile);
			System.out.println(fnx);
			System.out.println("HERE");
		} catch (Exception x) {
			System.out.println("Exception: " + x);
			x.printStackTrace();
		}

		return (res);
	}

	
	static String pkgPath(String p) {
		if (CPSReasoner.class.getResource(p) == null) {
			System.out.println("ERROR: path does not exist: " + p);
			return ("");
		}
		return (CPSReasoner.class.getResource(p).getPath());
	}

	static void writeToFile(String file, String str) throws IOException {
		PrintWriter out = new PrintWriter(file);
		out.print(str);
		out.close();
	}

	static String jenaToASP(Vector<String> in) {
		String aspProg;

		aspProg = "";
		for (int i = 3; /* first 4 lines are headers */
				i < in.size() - 1; /* last line is footer */
				i++) {
			String line = in.elementAt(i);
			/* first "|" mapped to 'input("' */
			line = line.replaceFirst("^\\| *", "input(\"");
			/* last "|" mapped to '").' */
			line = line.replaceFirst(" *\\|$", "\").");
			/* intermediate "|" mapped to '","' */
			line = line.replaceAll(" *\\| *", "\",\"");
			/* 'cvast:' prefix removed */
			line = line.replaceAll("cvast:", "");
			/* 'myprefix:' prefix removed */
			line = line.replaceAll("myprefix:", "");
			/*
			 * Replace '""' by '"'. Double quotes are generated by the above replacements if
			 * jena returned a string. Jena returns strings enclosed in double quotes.
			 */
			line = line.replaceAll("\"\"", "\"");
			/* ^^xsd:int" removed */
			line = line.replaceAll("\\^\\^xsd:int\"", "");
			aspProg += line + "\n";
		}
		return (aspProg);
	}

	static Vector<String> runCmdRaw(String cmd) throws IOException {
		return (runCmdRaw(cmd, null));
	}

	static Vector<String> runCmdRaw(String cmd, String inData) throws IOException {
		Runtime r = Runtime.getRuntime();

		System.out.println("Executing: " + cmd);
		Process p = r.exec(cmd);

		Thread outputFiller = null;
		if (inData != null) {
			outputFiller = new Thread(new StreamFiller(p.getOutputStream(), inData));
			outputFiller.start();
			// PrintWriter out=new PrintWriter(p.getOutputStream());
			// out.print(inData);
			// out.close();
		}

//		p.waitFor();

		/* retrieve the output */
		Vector<String> res = new Vector<String>();
		try {
			ReadStream s1, s2;
			s1 = new ReadStream("stdout", p.getInputStream(), res, false);
			s2 = new ReadStream("stderr", p.getErrorStream(), res, true);
			s1.start();
			s2.start();
			p.waitFor();
			s2.waitFor();
			s1.waitFor();
			if (outputFiller != null)
				outputFiller.join();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (p != null)
				p.destroy();
		}

		return (res);
	}

	static String runCmd(String cmd) throws IOException {
		return (runCmd(cmd, null));
	}

	static String runCmd(String cmd, String inData) throws IOException {
		String res;

		res = "";
		for (String line : runCmdRaw(cmd, inData))
			res = res + line + "\n";

		return (res);
	}

	static String cmdOutToString(Vector<String> out) {
		String res;

		res = "";
		for (String line : out)
			res = res + line + "\n";

		return (res);
	}

	static String ontologyFileList(String ontologyDir) throws IOException {
		return (ontologyFileList(ontologyDir, ""));
	}

	static String ontologyFileList(String ontologyDir, String prefix) throws IOException {
		String list;

		File[] ontologyFiles = new File(ontologyDir).listFiles();

		list = "";
		Arrays.sort(ontologyFiles);
		for (File f : ontologyFiles) {
			String str;

			str = f.toString();
			if (str.endsWith(".owl")) {
				if (str.indexOf(" ") >= 0) {
					throw (new IOException("No spaces allowed in filenames. Aborting. Offending filename: " + str));
				}
				if (isWindows()) { /*
									 * For jena to work properly under Windows with X:/... paths, we need to prefix
									 * the path with "/". Since this does not affect pellet, we do it regardless of
									 * reasoner.
									 */
					if (str.indexOf(":") >= 0)
						str = "/" + str;
				}
				list += prefix + str + " ";
			}
		}

		return (list);
	}

	static String pelletCmd(String queryFile, String ontologyDir, boolean useXML) throws IOException {
		String cmd;

		// cmd="./pelletcli/bin/";
		cmd = pkgPath("pelletcli/bin/");
		if (isWindows())
			cmd += "pellet.bat";
		else
			cmd += "pellet";
		cmd += " query ";
		if (useXML)
			cmd += "-o XML ";
		cmd += "-q " + queryFile;// +" "+ontologyFile;
//		cmd+=" query -o XML -q "+tmpFile;//+" "+ontologyFile;
//		cmd+=" query -o JSON -q "+tmpFile;//+" "+ontologyFile;

		cmd += " ";
		cmd += ontologyFileList(ontologyDir);

		return (cmd);
	}

	static String jenaCmd(String queryFile, String ontologyDir) throws IOException {
		String cmd = pkgPath("");

		if (isWindows())
			cmd += "runjena.bat";
		else
			cmd += "./runjena.sh";
		cmd += " --file=" + queryFile;

		cmd += " ";
		cmd += ontologyFileList(ontologyDir, "--data=").replace("\\", "/");

		cmd += "--results=text ";

		return (cmd);
	}

	static String clingoCmd(String aspFile) {
		String cmd;

		// cmd="./clingo-4.4.0/";
		cmd = pkgPath("clingo-4.4.0/");
		if (isWindows())
			cmd += "clingo.exe";
		else if (isMacOSX())
			cmd += "clingo-macosx";
		else
			cmd += "clingo-linux-x86";
		cmd += " " + aspFile;

		return (cmd);
	}

	static String clingoCmdAll(String aspFile) {
		String cmd;

		// cmd="./clingo-4.4.0/";
		cmd = pkgPath("clingo-4.4.0/");
		if (isWindows())
			cmd += "clingo.exe";
		else if (isMacOSX())
			cmd += "clingo-macosx";
		else
			cmd += "clingo-linux-x86";
		cmd += " 0 " + aspFile;

		return (cmd);
	}

	static String dlvCmd(String aspFile) {
		String cmd;

		// cmd="./clingo-4.4.0/";
		cmd = pkgPath("dlv/");
		if (isWindows())
			cmd += "dlv.mingw.exe";
		else if (isMacOSX())
			cmd += "dlv-macosx";
		else
			cmd += "dlv";
		cmd += " -n=1 " + aspFile;

		return (cmd);
	}

	static String dlvCmdAll(String aspFile) {
		String cmd;

		// cmd="./clingo-4.4.0/";
		cmd = pkgPath("dlv/");
		if (isWindows())
			cmd += "dlv.mingw.exe";
		else if (isMacOSX())
			cmd += "dlv-macosx";
		else
			cmd += "dlv";
//		cmd+=" -n=1 "+aspFile;
		cmd += " " + aspFile;

		return (cmd);
	}

	static String mkatomsCmd() {
		String cmd;

		cmd = pkgPath("mkatoms/");
		if (isWindows())
			cmd += "mkatoms.exe";
		else if (isMacOSX())
			cmd += "mkatoms-macosx";
		else
			cmd += "mkatoms-linux-x86";

		return (cmd);
	}

	static boolean isWindows() {
		String OS = System.getProperty("os.name");
		System.out.println("os=" + OS);
		return (OS.startsWith("Windows"));
	}

	static boolean isMacOSX() {
		String OS = System.getProperty("os.name");
		return (OS.startsWith("Mac"));
	}

	static String readFile(String s) throws FileNotFoundException, IOException {
		BufferedReader br = new BufferedReader(new FileReader(s));
		String res = readFile(br);
		br.close();
		return (res);
	}

	static String readFile(File f) throws FileNotFoundException, IOException {
		BufferedReader br = new BufferedReader(new FileReader(f));
		String res = readFile(br);
		br.close();
		return (res);
	}

	static String readFile(BufferedReader br) throws IOException {
		String str = "", line;
		while ((line = br.readLine()) != null)
			str = str + line + "\n";
		return (str);
	}
}

class modelEnumerator {
	static Vector<Vector<String>> models(Vector<String> in) throws IOException {
		Vector<Vector<String>> models = new Vector<Vector<String>>();

		boolean started = false;
		int i = 0;
		for (String line : in) {
			if (!started) {
				models.add(new Vector<String>());
				started = true;
			}

			models.elementAt(i).add(line);

			if (line.startsWith("::endmodel")) {
				i++;
				started = false;
			}
		}
		return (models);
	}
}

class aspToOWLoutput {
	String[] lines;
	Vector<Integer> maxlens;
	int headings_line;
	int n_answers;

	// https://stackoverflow.com/questions/40770990/split-string-by-comma-but-ignore-commas-in-brackets-or-in-quotes
    // final static String commaRegex=",(?=([^\"]*\"[^\"]*\")*[^\"]*$)";
    // final static String commaRegex=",(?=[^\\)]*(?:\\(|$))";
	final static String commaRegex = ",(?=([^\"]*\"[^\"]*\")*[^\"]*$)(?=[^\\)]*(?:\\(|$))";

	String process(Vector<String> in) throws IOException {
		String res;

		headings_line = -1;
		n_answers = 0;

		extractOutputLines(in);

		sortLines();

		res = linesToString();

		res = "Query Results (" + n_answers + " answers): \n-------------------------------------------- \n" + res;

		return (res);
	}

	/*
	 * Sets fields lines, maxlens, headings_line, n_answers
	 */
	void extractOutputLines(Vector<String> in) throws IOException {
		String cmd;

		lines = new String[in.size()];
		/* initialize the array to empty strings */
		for (int i = 0; i < in.size(); i++)
			lines[i] = "";

		// int headings_line=-1;

		// int n_answers=0;

		maxlens = null;
		int n_args = 0;
		int n_line = 0;
		for (String line : in) {
			boolean is_headings;
			String newline;

			if (line.startsWith("::endmodel")) {
				lines[n_line] = line;
				break;
			}

			if (!line.startsWith("output(") && !line.startsWith("output_headings("))
				continue;

			is_headings = line.startsWith("output_headings(");
			if (is_headings)
				headings_line = n_line;
			else
				n_answers++;

			if (1 == 0) { /* '"' are removed */
				newline = line.replaceAll("\"", "");
				/* 'output(' replaced by "" */
				newline = newline.replaceFirst("^output\\(|^output_headings\\(", "");
				/* ')' replaced by "" */
				newline = newline.replaceFirst("\\)$", "");
			}
			
			/* ThanhNH : Rule is reading output(.) and output_headings(.) only */
			
			/* 'output(' replaced by "" */
			newline = line.replaceFirst("^output\\(|^output_headings\\(", "");
			/* ')' replaced by "" */
			newline = newline.replaceFirst("\\)$", "");

			lines[n_line++] = newline;

//			String args[]=newline.split(",");
//			String args[]=newline.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1);
			String args[] = newline.split(commaRegex, -1);

			/* count the number of arguments; doing it once is enough */
			if (n_args == 0) {
				n_args = args.length;
				// line.length() - line.replace(",", "").length() + 1;
				maxlens = new Vector<Integer>();
				for (int i = 0; i < n_args; maxlens.addElement(0), i++)
					;
			}

			for (int i = 0; i < args.length; i++) {
				if (args[i].length() > maxlens.elementAt(i))
					maxlens.setElementAt(args[i].length(), i);
			}
		}
	}

	/* Sorts lines, paying attention to the headings line, if present */
	void sortLines() {
		String headtxt = "";
		if (headings_line >= 0) {
			headtxt = lines[headings_line];
			lines[headings_line] = "";
		}

		/* Sort the lines */
		Arrays.sort(lines);
		if (headings_line >= 0) {
			String[] inter = new String[lines.length + 2];
			for (int i = 0; i < lines.length; i++)
				inter[i + 2] = lines[i];
			inter[0] = headtxt;
			lines = inter;
		}
	}

	/* Converts the lines to a string and adds the headings separator, if needed */
	String linesToString() {
		String res;

		res = "";
		for (String line : lines) {
			String newline;

			if (line == null || line.equals(""))
				continue;

			if (line.startsWith("::endmodel"))
				continue;
			// break;

//			String args[]=line.split(",");
			String args[] = line.split(commaRegex, -1);

			for (int i = 0; i < args.length; i++) {
				if (args[i].length() > 0 && args[i].charAt(0) == '"' && args[i].charAt(args[i].length() - 1) == '"')
					args[i] = args[i].substring(1, args[i].length() - 1);
				/* '\"' are mapped to '"' */
				args[i] = args[i].replaceAll("\\\\\"", "\"");
				// args[i]=args[i].replaceAll("\\\\","xxx");
			}

			newline = "";
			for (int i = 0; i < args.length; i++) {
				int diff = maxlens.elementAt(i) - args[i].length();
				newline += args[i];
				for (int j = 0; j < diff; j++)
					newline += " ";
				if (i < args.length - 1)
					newline += " | ";
			}

			// newline="| "+newline+" |";
			if (headings_line >= 0 && res.equals("")) { /* This is a headings line */
				newline = newline + "\n" + newline.replaceAll(".", "=");
			}
			res += newline + "\n";
		}

		return (res);
	}
}

class ReadStream implements Runnable {
	String name;
	InputStream is;
	Vector<String> v;
	boolean discard;
	Thread thread;

	public ReadStream(String name, InputStream is, Vector<String> v, boolean discard) {
		this.name = name;
		this.is = is;
		this.v = v;
		this.discard = discard;
	}

	public void start() {
		thread = new Thread(this);
		thread.start();
	}

	public void waitFor() throws InterruptedException {
		thread.join();
	}

	public void run() {
		try {
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			while (true) {
				String s = br.readLine();
				if (s == null)
					break;

				if (discard)
					System.out.println("[" + name + "] " + s);
				else
					v.addElement(s);
			}
			is.close();
		} catch (Exception ex) {
			System.out.println("Problem reading stream " + name + "... :" + ex);
			ex.printStackTrace();
		}
	}
}

class StreamFiller implements Runnable {
	private final OutputStream os;
	private final String inData;

	StreamFiller(OutputStream os, String inData) {
		this.os = os;
		this.inData = inData;
	}

	public void run() {
		PrintWriter out = new PrintWriter(os);
		out.print(inData);
		out.close();
	}
}
