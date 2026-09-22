package Homework1;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

// TODO: Only see a single command_file.txt as an input?
// TODO: Do we just send the whole command to the server, and parse it there as well?

public class BookClient {
    public static String bidirectionalUDP(String hostAddress, int udpPort, String command, PrintWriter fileOutputWriter) {
        String receiveCommand = "";

        try{
            DatagramSocket datasocket = new DatagramSocket();
            DatagramPacket sendPacket, receivePacket;
            InetAddress address = InetAddress.getByName(hostAddress);

            byte[] sendBuffer = new byte[1024];
            byte[] receiveBuffer = new byte[1024];

            sendBuffer = command.getBytes();

            sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, address, udpPort);
            receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

            datasocket.send(sendPacket);
            datasocket.receive(receivePacket);

            receiveCommand = new String(receivePacket.getData(), 0, receivePacket.getLength());

            if (!receiveCommand.isEmpty()) {
                String[] responses = receiveCommand.split("%");
                for (String response : responses) {
                    // System.out.println("  -> " + response);
                    fileOutputWriter.println(response);
                }
            }

            datasocket.close();

            return receiveCommand;

        } catch (SocketException e) {
            System.err.println(e);
        } catch (IOException e) {
            System.err.println(e);
        }

        return receiveCommand;
    }

    public static void bidirectionalTCP(Socket tcpSocket, String command, PrintWriter fileOutputWriter) {
        String responseMessage;

        // Client --> Server
        OutputStreamWriter osw;
        PrintWriter clientWriter;

        // Server --> Client
        InputStreamReader isr;
        BufferedReader clientReader;

        try {
            isr = new InputStreamReader(tcpSocket.getInputStream());
            clientReader = new BufferedReader(isr);

            osw = new OutputStreamWriter(tcpSocket.getOutputStream());
            clientWriter = new PrintWriter(osw, true);

            clientWriter.println(command);

            while ((responseMessage = clientReader.readLine()) != null) {
                // TODO: Needed some way to terminate with multi-line messages
                if (responseMessage.isEmpty()) {
                    break;
                }

                fileOutputWriter.println(responseMessage);
                // System.out.println("Response: '" + responseMessage + "'");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String hostAddress;
        int tcpPort;
        int udpPort;
        int clientId;

        if (args.length != 2) {
            System.out.println("ERROR: Provide 2 arguments: command-file, clientId");
            System.out.println("\t(1) command-file: file with commands to the server");
            System.out.println("\t(2) clientId: an integer between 1..9");
            System.exit(-1);
        }

        String commandFile = args[0];
        clientId = Integer.parseInt(args[1]);
        hostAddress = "localhost";
        tcpPort = 7000;// hardcoded -- must match the server's tcp port
        udpPort = 8000;// hardcoded -- must match the server's udp port

        // "The default mode of communication is UDP"
        boolean tcpMode = false;
        Socket tcpSocket = null;

        try {
            String clientOutputFilename = "out_" + clientId + ".txt";
            FileWriter clientOutputFileWriter = new FileWriter(clientOutputFilename);
            PrintWriter clientOutputPrintWriter = new PrintWriter(clientOutputFileWriter);
            Scanner sc = new Scanner(new FileReader(commandFile));

            while (sc.hasNextLine()) {
                String cmd = sc.nextLine();
                String[] tokens = cmd.split(" ");

                // ------------------------------------------------------------------
                if (tokens[0].equals("set-mode")) {
                    if (tokens[1].equals("u")) {
                        bidirectionalUDP(hostAddress, udpPort, cmd, clientOutputPrintWriter);
                    }
                    else if (tokens[1].equals("t")) {
                        tcpMode = true;
                        tcpSocket = new Socket(hostAddress, tcpPort);
                        bidirectionalTCP(tcpSocket, cmd, clientOutputPrintWriter);
                    }
                // ------------------------------------------------------------------
                } else if (tokens[0].equals("begin-loan")) {
                    if (tcpMode) {
                        bidirectionalTCP(tcpSocket, cmd, clientOutputPrintWriter);
                    } else {
                        bidirectionalUDP(hostAddress, udpPort, cmd, clientOutputPrintWriter);
                    }
                // ------------------------------------------------------------------
                } else if (tokens[0].equals("end-loan")) {
                    if (tcpMode) {
                        bidirectionalTCP(tcpSocket, cmd, clientOutputPrintWriter);
                    } else {
                        bidirectionalUDP(hostAddress, udpPort, cmd, clientOutputPrintWriter);
                    }
                // ------------------------------------------------------------------
                } else if (tokens[0].equals("get-loans")) {
                    if (tcpMode) {
                        bidirectionalTCP(tcpSocket, cmd, clientOutputPrintWriter);
                    } else {
                        bidirectionalUDP(hostAddress, udpPort, cmd, clientOutputPrintWriter);
                    }
                // ------------------------------------------------------------------
                } else if (tokens[0].equals("get-inventory")) {
                    if (tcpMode) {
                        bidirectionalTCP(tcpSocket, cmd, clientOutputPrintWriter);
                    } else {
                        bidirectionalUDP(hostAddress, udpPort, cmd, clientOutputPrintWriter);
                    }
                // ------------------------------------------------------------------
                } else if (tokens[0].equals("exit")) {
                    if (tcpMode) {
                        bidirectionalTCP(tcpSocket, cmd, clientOutputPrintWriter);
                    } else {
                        bidirectionalUDP(hostAddress, udpPort, cmd, clientOutputPrintWriter);
                    }
                // ------------------------------------------------------------------
                } else {
                    System.out.println("ERROR: No such command");
                }
            }

            clientOutputPrintWriter.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}