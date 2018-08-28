import java.io.*;
import java.net.*;
import java.util.Arrays;

public class CSftp {

    private Socket ftpSocket = null;
    private BufferedReader in = null;
    private BufferedWriter out = null;
    private BufferedReader stdIn = null;
    private static final int MAX_LEN = 4096;
    private String fromServer;
    private String fromUser;
    private Socket dataSocket = null;


    public static void main(String[] args){


        int portNumber;
        String ipAddress;

        if ((args.length != 1) && (args.length != 2)) {
            System.err.println("Invalid arguements Usage: FTPClient <IP address of host name> <port number of server>");
            System.exit(1);
        }

        ipAddress = args[0];

        if(args.length == 1){
            portNumber = 21;
        }
        else{
            portNumber = Integer.parseInt(args[1]);
        }

        CSftp ftp = new CSftp();
        ftp.runFTP(ipAddress, portNumber);

    }

    private void runFTP(String ipAddress, int portNumber) {
        byte cmdString[] = new byte[MAX_LEN];
        try {
//            System.out.println("Trying");
            ftpSocket = new Socket(ipAddress,portNumber);
            out = new BufferedWriter(new OutputStreamWriter(ftpSocket.getOutputStream()));
            in = new BufferedReader(
                    new InputStreamReader(ftpSocket.getInputStream()));
            stdIn = new BufferedReader(new InputStreamReader(System.in));
        } catch(UnknownHostException e){
            System.err.println("0xFFFC Control connection to " + ipAddress + " on port " + portNumber + ".0 failed to open");
            System.exit(1);
        }
        catch(IOException exception) {
            System.err.println("0xFFFD Control connection I/O error, closing control connection");
            disconnect();
            System.exit(1);
        }

        try{

            parserServerResponse();
            System.out.print("csftp> ");
            fromUser = stdIn.readLine();
            while (true) {
                int valid = parserUserInput();
                if (valid == 0){
                    System.out.println("0x001 Invalid command");
                }
                else if (valid == 1){
                    if (fromUser.trim().startsWith("user")){
                        user(fromUser);
                    }
                    else if (fromUser.trim().startsWith("pw")){
                        pw(fromUser);
                    }
                    else if (fromUser.trim().startsWith("quit")){
                        quit();
                    }
                    else if (fromUser.trim().startsWith("cd")){
                        cd();
                    }
                    else if (fromUser.trim().startsWith("features")){
                        features();
                    }
                    else if (fromUser.trim().startsWith("dir")){
                        dir();
                    }
                    else if (fromUser.trim().startsWith("get")){
                        get();
                    }
                }
                System.out.print("csftp> ");
                fromUser = stdIn.readLine();
            }
        }
        catch(UnknownHostException e){
            System.err.println("0xFFFC Control connection to " + ipAddress + " on port " + portNumber + ".0 failed to open");
            System.exit(1);
        }
        catch(IOException exception){
            System.err.println("998 Input error while reading commands, terminating.");
        }
    }

    private void get() {
        String[] commands = fromUser.trim().split("\\s");
        if (commands.length < 2){
            System.out.println("0x002 Incorrect number of arguments");
            return;
        }
        try {
            System.out.println("--> PASV");
            out.write("PASV\n");
            out.flush();
            parserServerResponse();
            if(dataSocket != null){
                String onCommand = "RETR";
                for (int i=1; i<commands.length; i++){
                    onCommand+=" "+commands[i];
                }
                System.out.println("--> "+onCommand);
                out.write(onCommand+"\n");
                out.flush();
                parserServerResponse();
                if(dataSocket == null){
                    return;
                }
                byte[] cmdString = new byte[MAX_LEN];
                try{
                    DataInputStream inDataStream = new DataInputStream(
                            new BufferedInputStream(dataSocket.getInputStream()));
                    try{
                        inDataStream.readFully(cmdString);
                    }
                    catch (EOFException e){
//                        Ignore
                    }
                    try{
                        FileOutputStream fileStream = new FileOutputStream(commands[1]);
                        cmdString = trimBinary(cmdString);
                        fileStream.write(cmdString);
                        fileStream.close();
                        inDataStream.close();
                    }
                    catch (IOException e){
                        System.out.println("0x38E Access to local file " + commands[1]+" denied.");
                        return;
                    }
                    dataSocket.close();
                    parserServerResponse();
                }
                catch (IOException e){
                    System.out.println("0x3A7 Data transfer connection I/O error, closing data connection.");
                    dataSocket.close();
                    return;
                }
            }
        }
        catch (IOException e) {
            System.err.println("0xFFFD Control connection I/O error, closing control connection");
            disconnect();
            System.exit(1);
        }
    }

    private void dir() {
        String[] commands = fromUser.trim().split("\\s");
        if (commands.length < 1){
            System.out.println("0x002 Incorrect number of arguments");
            return;
        }
        try {
            System.out.println("--> PASV");
            out.write("PASV\n");
            out.flush();
            parserServerResponse();
            if(dataSocket != null){
                String onCommand = "LIST";
                for (int i=1; i<commands.length; i++){
                    onCommand+=" "+commands[i];
                }
                System.out.println("--> "+onCommand);
                out.write(onCommand+"\n");
                out.flush();
                parserServerResponse();
                if (dataSocket == null){
                    return;
                }
                byte[] cmdString = new byte[MAX_LEN];
                try{
                    DataInputStream inDataStream = new DataInputStream(
                            new BufferedInputStream(dataSocket.getInputStream()));
                    try{
                        inDataStream.readFully(cmdString);
                    }
                    catch (EOFException e){
//                        Ignore
                    }
                    String filesReceived = new String(cmdString);
                    System.out.println(filesReceived.trim());
                    parserServerResponse();
                    dataSocket.close();
                    inDataStream.close();
                }
                catch (IOException e){
                    System.out.println("0x3A7 Data transfer connection I/O error, closing data connection.");
                    dataSocket.close();
                    return;
                }
            }
        }
        catch (IOException e) {
            System.err.println("0xFFFD Control connection I/O error, closing control connection");
            disconnect();
            System.exit(1);
        }
    }

    private void features() {
        String[] commands = fromUser.trim().split("\\s");
        try {
            String cdCommand = "FEAT";
            for (int i=1;i<commands.length;i++){
                cdCommand = cdCommand+" "+commands[i];
            }
            System.out.println("--> "+cdCommand);
            out.write(cdCommand.trim() + "\n");
            out.flush();
            parserServerResponse();
            while (!((fromServer = in.readLine()).startsWith("211"))) {
                System.out.println("<-- " + fromServer);
            }
        }
        catch (IOException e) {
            System.err.println("0xFFFD Control connection I/O error, closing control connection");
            disconnect();
            System.exit(1);
        }
    }

    private void cd() {
        String[] commands = fromUser.trim().split("\\s");
        try {
            String cdCommand = "";
            for (int i=1;i<commands.length;i++){
                cdCommand = cdCommand+commands[i]+" ";
            }
            System.out.println("--> CWD " + cdCommand.trim());
            out.write("CWD " + cdCommand.trim() + "\n");
            out.flush();
            parserServerResponse();
        }
        catch (IOException e) {
            System.err.println("0xFFFD Control connection I/O error, closing control connection");
            disconnect();
            System.exit(1);
        }
    }

    private void quit() {
        String[] commands = fromUser.trim().split("\\s");
        if (commands.length > 1) {
            System.out.println("0x002 Incorrect number of arguments");
        } else {
            try {
                System.out.println("--> QUIT");
                out.write("QUIT\n");
                out.flush();
                parserServerResponse();
            }
            catch (IOException e) {
                System.err.println("0xFFFD Control connection I/O error, closing control connection");
                disconnect();
                System.exit(1);
            }
        }
    }

    private void user(String fromUser) {
        String[] commands = fromUser.trim().split("\\s");
        if (commands.length > 3){
            System.out.println("0x002 Incorrect number of arguments");
        }
        else{
            try {
                if(commands.length == 3){
                    System.out.println("--> USER " + commands[1]);
                    out.write("USER " + commands[1] + "\n");
                    out.flush();
                    parserServerResponse();
                    System.out.println("--> PASS " + commands[2]);
                    out.write("PASS " + commands[2] + "\n");
                    out.flush();
                }
                else{
                    System.out.println("--> USER " + commands[1]);
                    out.write("USER " + commands[1] + "\n");
                    out.flush();
                }
                parserServerResponse();
            } catch (IOException e) {
                System.err.println("0xFFFD Control connection I/O error, closing control connection");
                disconnect();
                System.exit(1);
            }
        }
    }

    private void pw(String fromUser){
        String[] commands = fromUser.trim().split("\\s");
        if (commands.length > 2){
            System.out.println("0x002 Incorrect number of arguments");
        }
        else{

            try {
                System.out.println("--> PASS " + commands[1]);
                out.write("PASS " + commands[1] + "\n");
                out.flush();
            } catch (IOException e) {
                System.err.println("0xFFFD Control connection I/O error, closing control connection");
                disconnect();
                System.exit(1);
            }
            parserServerResponse();
        }
    }

    private int parserUserInput() {
        if(fromUser.startsWith("#") || fromUser.isEmpty() ){
            return 2;
        }
        else if (fromUser.contains("user ") || fromUser.contains("pw ") ||fromUser.contains("cd ") ||fromUser.contains("dir") ||fromUser.contains("get ") || fromUser.contains("quit") || fromUser.contains("features")){
            return 1;
        }
        else{
            return 0;
        }


    }

    private void parserServerResponse() {
        try {
            String s;
            s=in.readLine();
            while (s!=null){
                fromServer += s; 
                s=in.readLine();
                System.out.print("s="+s);
                // in.readLine();
            }
        } catch (IOException e) {
            System.err.println("0xFFFD Control connection I/O error, closing control connection");
            disconnect();
            System.exit(1);
        }
        if (fromServer.startsWith("220")){
            System.out.println("<-- "+fromServer);
            System.out.println("Connection Established");
        }
        else  if (fromServer.startsWith("421")){
            System.out.println("0xFFFF Processing error. Service not available try again later");
            System.exit(1);

        }
        else  if (fromServer.startsWith("530")){
            System.out.println("<-- "+fromServer);
//            System.out.println("530 Invalid Username or Password. Not Logged in");
        }
        else  if (fromServer.startsWith("221")){
            System.out.println("<-- "+fromServer);
//            System.out.println(fromServer);
            disconnect();
            System.exit(0);
        }
        else  if (fromServer.startsWith("211")){
            System.out.println("<-- "+fromServer);
//            System.out.println("Features available:");
        }
        else  if (fromServer.startsWith("227")){
            System.out.println("<-- "+fromServer);
            String toSplit = fromServer.substring(fromServer.indexOf('(') + 1, fromServer.indexOf(')'));
            String ip;
            int port;
            if (toSplit.length() > 0) {
                String[] ipAndPort = toSplit.split(",");
                ip = ipAndPort[0] + "." + ipAndPort[1] + "." + ipAndPort[2] + "." + ipAndPort[3];
                port = Integer.parseInt(ipAndPort[4]) * 256 + Integer.parseInt(ipAndPort[5]);
//                System.out.println("Making data connection with host:" + ip + " port:" + port);
                try {
                    dataSocket = new Socket(ip, port);
                } catch (IOException e) {
                    System.out.println("0x3A2 Data transfer connection to "+ ip +" on port "+ port + " failed to open");
                    return;
                }
            } else {
                System.out.println("0xFFFF Processing error. Invalid ip and port provided by server. Cannot connect");
            }
        }
        else  if (fromServer.startsWith("550") || fromServer.startsWith("551") || fromServer.startsWith("552") || fromServer.startsWith("553")){
            System.out.println("<-- "+fromServer);
            try {
                if (dataSocket!=null){
                    dataSocket.close();
                    return;
                }
            } catch (IOException e) {
                System.err.println("0xFFFD Control connection I/O error, closing control connection");
                disconnect();
                System.exit(1);
            }
        }
        else{
            System.out.println("<-- "+fromServer);
        }
    }

    private void disconnect() {
        try {
            ftpSocket.close();
            in.close();
            out.close();
            stdIn.close();
            if(dataSocket!=null){
                dataSocket.close();
            }
        } catch (IOException e) {

        }
    }

    private byte[] trimBinary(byte[] cmdString) {
        int i = cmdString.length-1;
        while(i>=0 && cmdString[i]==0){
            i--;
        }
        return Arrays.copyOf(cmdString, i + 1);
    }
}




