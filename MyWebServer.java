/** Name: Sarah Melvin
 *  Class: Network Systems and Design
 *  Project 1: Web Server
 *  Name: Sarah Melvin
 *
 *  This is my submission for project 1. As stated in the email, I am using 3 of my slip days to submit this.
 *
 */

// package com.company;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;


public class MyWebServer {
    public static void main (String args[]) {
        try {
            // Get the port to listen on
            int port = Integer.parseInt(args[0]);
            String url = args[1];

            // Create a ServerSocket to listen on that port.
            ServerSocket ss = new ServerSocket(port);

            // enter an infinite loop, waiting for & handling connections.
            while (true) {
                // Wait for a client to connect
                Socket client = ss.accept();

                // Get input and output streams to talk to the client
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter out = new PrintWriter(client.getOutputStream());

                // initialize HTTPRequest object, begin parsing
                HTTPRequest request = new HTTPRequest(in, url);
                int code = request.parseRequest();
                request.truncate();

                // find the file based on the param input
                String fileName = request.getFileName();
                File file = new File( fileName + ".html");

                // make the url for the reply
                request.concatenate(port);

                // begin creating HttpResponse
                String replyStatus = request.getHttpReply(code);
                String command = request.getMethod();
                HTTPResponse response;
                if (file == null) {
                    response = new HTTPResponse(command);
                }
                else { response = new HTTPResponse(file, replyStatus, command); }

                // print the response in the browser
                response.printResponse(out);

                // Close socket, breaking the connection to the client, and
                // closing the input and output streams
                out.close(); // Flush and close the output stream
                in.close(); // Close the input stream
                client.close(); // Close the socket itself
            } // Now loop again, waiting for the next connection
        }
        // If anything goes wrong, print an error message
        catch (Exception e) {
            System.err.println(e);
            System.err.println("Usage: java HttpMirror <port>");
        }
    }
}

class HTTPRequest {
    private static final String[][] HttpReplies = { {"200", "OK"},
                                                    {"304", "Not Modified"},
                                                    {"400", "Bad Request"},
                                                    {"404", "Not Found"},
                                                    {"501", "Not Implemented"} };
    private Hashtable headers, params;
    private int[] ver;
    public BufferedReader request;
    public String rootPath, method, url;
    public int status = 200; // this is a catch for if-modified-since errors


    // constructor
    public HTTPRequest(BufferedReader req, String theUrl) {
        request = req;
        rootPath = theUrl;
        url = "";
        ver = new int[2];
        method = "";
        headers = new Hashtable();
        params = new Hashtable();
    }

    // truncate the url param
    public void truncate() {
        // If it is an absoluteURI request, get rid of everything before the third '/'
        if (rootPath.charAt(0) == 'h') {
            int count = 0;
            int cut = 0;
            for (int i = 0; i < rootPath.length(); i++) {
                if (rootPath.charAt(i) == '/') {
                    count += 1;
                }
                if (count == 3) {
                    cut = i;
                    break;
                }
            }
            rootPath = rootPath.substring(cut, rootPath.length());
        }
        else if (rootPath.charAt(0) == '~') {
            rootPath = rootPath.substring(1, rootPath.length());
        }

        // if it is a directory, append index.html
        if (rootPath.charAt(0) == '/' && rootPath.length() == 1) {
            rootPath = rootPath + "index.html";
        }
    }

    public String getFileName() {
        int idx = rootPath.lastIndexOf('/') + 1;
        String fileName = rootPath.substring(idx, rootPath.length());
        if ((fileName.length() > 5) && (fileName.substring(fileName.length()-5, fileName.length()).equals(".html"))){
            fileName = fileName.substring(0, fileName.length()-5);
        }
        return fileName;
    }

    public void concatenate(int port) {
        // concatenate
        String concatURL = "http://localhost:" + port;
        url = concatURL + rootPath;
    }

    public int parseRequest() throws Exception {
        String initial, cmd[], prms[], temp[];
        int ret, idx, i;

        ret = 200; // default is OK now
        initial = request.readLine();
        if (initial == null || initial.length() == 0) return 0;
        if (Character.isWhitespace(initial.charAt(0))) {
            // starting whitespace, return bad request
            return 400;
        }

        cmd = initial.split("\\s");
        if (cmd.length != 3) {
            return 400;
        }

        if (cmd[2].indexOf("HTTP/") == 0 && cmd[2].indexOf('.') > 5) {
            temp = cmd[2].substring(5).split("\\.");
            try {
                ver[0] = Integer.parseInt(temp[0]);
                ver[1] = Integer.parseInt(temp[1]);
            } catch (NumberFormatException nfe) {
                ret = 400;
            }
        } else ret = 400;

        method = cmd[0];

        idx = cmd[1].indexOf('?');
        if (idx >= 0) {
            url = URLDecoder.decode(cmd[1].substring(0, idx), "ISO-8859-1");
            prms = cmd[1].substring(idx + 1).split("&");

            params = new Hashtable();
            for (i = 0; i < prms.length; i++) {
                temp = prms[i].split("=");
                if (temp.length == 2) {
                    // use ISO-8859-1 as temporary charset and then
                    // String.getBytes("ISO-8859-1") to get the data
                    params.put(URLDecoder.decode(temp[0], "ISO-8859-1"),
                            URLDecoder.decode(temp[1], "ISO-8859-1"));
                } else if (temp.length == 1 && prms[i].indexOf('=') == prms[i].length() - 1) {
                    // handle empty string separately
                    params.put(URLDecoder.decode(temp[0], "ISO-8859-1"), "");
                }
            }
        }

        parseHeaders();
        if (headers == null) ret = 400; // gtfo client

        else if (headers.containsKey("if-modified-since")) {
            ret = isModifiedSince(headers.get("if-modified-since").toString());
            if (status == 400) { ret = 400; }
        }

        else if (cmd[0].equals("POST")) {
            ret = 501; // not implemented
        }

        else if (ver[0] == 1 && ver[1] >= 1) {
            if (cmd[0].equals("OPTIONS") ||
                    cmd[0].equals("PUT") ||
                    cmd[0].equals("DELETE") ||
                    cmd[0].equals("TRACE") ||
                    cmd[0].equals("CONNECT")) {
                ret = 501; // not implemented
            }
        }

        else { ret = 400; } // client is being stupid & we don't know wtf they want

        return ret;

    }

    private int isModifiedSince(String date) {
        int ret; // 400 by default
        File file = new File(getFileName());
        Date modDate = new Date(file.lastModified());
        Date askDate = null;
        DateFormat format = new SimpleDateFormat("MM/dd/yyy HH:mm:ss");
        try {
            askDate = format.parse(date);
        } catch (Exception e) { e.printStackTrace(); }

        if (askDate.before(modDate)) {
            ret = 200;
        }
        else {
            ret = 304;
        }
        return ret;
    }


    private void parseHeaders() throws Exception {
        String line;
        int idx;

        line = request.readLine();
        while (!line.equals("")) {
            idx = line.indexOf(':');
            if (idx < 0) {
                headers = null;
                break;
            }
            else {
                headers.put(line.substring(0, idx).toLowerCase(), line.substring(idx+1).trim());
            }
            line = request.readLine();
        }
    }

    public String getMethod() {
        return method;
    }

    public static String getHttpReply(int code) {
        String key, ret;
        int i;

        ret = null;
        key = "" + code;
        for (i=0; i<HttpReplies.length; i++) {
            if (HttpReplies[i][0].equals(key)) {
                ret = code + " " + HttpReplies[i][1];
                break;
            }
        }

        return ret;
    }

}

class HTTPResponse {
    File fileFound;
    String responseCode;
    String command;

    public HTTPResponse (File file, String code, String method) {
        fileFound = file;
        responseCode = code;
        command = method;
    }

    // second constructor in case of 404
    public HTTPResponse (String method) {
        fileFound = null;
        responseCode = "404: Not Found";
        command = method;
    }

    public static String getDateHeader() {
        SimpleDateFormat format;
        String ret;
        format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        ret = "Date: " + format.format(new Date()) + " GMT\r\n";
        return ret;
    }

    public String getLastModifiedHeader() {
        SimpleDateFormat format;
        String ret;
        format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        ret = "Last-Modified: " + format.format(new Date(fileFound.lastModified())) + " GMT\r\n";
        return ret;
    }

    public void printResponse(PrintWriter out) throws IOException {

        // Start sending our reply, using the HTTP 1.1 protocol
        if (command.equals("GET")) {

            Scanner fileReader;
            try {
                fileReader = new Scanner(fileFound);

                out.print("hello world\r\n"); // DO NOT DELETE: headers will not print without this
                out.print("HTTP/1.1 " + responseCode + "\r\n"); // Version & status code
                out.print(getDateHeader());
                out.print("User-Agent: MyWebServer/0.1.1\r\n");
                out.print("Server: AwesomeServer/0.1.1\r\n");
                out.print(getLastModifiedHeader());
                out.print("Content-Type: text/plain\r\n"); // The type of data
                out.print("Connection: close\r\n"); // Will close stream
                out.print("\r\n"); // End of headers

                while (fileReader.hasNext()) {
                    out.println(fileReader.nextLine());
                }
            } catch (FileNotFoundException e) {
                out.print("hello world\r\n"); // DO NOT DELETE: for some reason, headers will not show up without this line of code
                out.print("HTTP/1.1 404: Not Found\r\n"); // Version & status code
                out.print("Content-Type: text/plain\r\n"); // The type of data
                out.print("Connection: close\r\n"); // Will close stream
                out.print("\r\n"); // End of headers

                // html body
                out.println("<html>");
                out.println("<head>");
                out.println("<title>404: Not Found</title>");
                out.println("</head>");
                out.println("<body bgcolor=\"white\">");
                out.println("</body>");
                out.println("</html>");
            }
        }
        else if (command.equals("HEAD")) {
            out.print("hello world\r\n"); // DO NOT DELETE: headers will not print without this
            out.print("HTTP/1.1 " + responseCode + "\r\n"); // Version & status code
            out.print("Content-Type: text/plain\r\n"); // The type of data
            out.print("Connection: close\r\n"); // Will close stream
            out.print("\r\n"); // End of headers
        }

    }
}