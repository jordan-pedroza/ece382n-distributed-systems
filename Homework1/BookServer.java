package Homework1;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class BookServer {
    // NOTE: Believe that LinkedHashMap should keep the keys in insertion order
    static int loanCount = 1;
    static Map<String, Integer> currentInventory = new LinkedHashMap<>();
    static Map<String, List<BookCheckout>> currentCheckouts = new LinkedHashMap<>();

    public static class BookCheckout {
        int loanid;
        String username;

        public BookCheckout(int loanid, String username) {
            this.loanid = loanid;
            this.username = username;
        }
    }

    public synchronized String beginLoan(String username, String bookname) {
        String responseMessage = "";
        if (!currentInventory.containsKey(bookname)) {
            responseMessage = "Request Failed - We do not have this book";
        } else if (currentInventory.get(bookname) == 0) {
            responseMessage = "Request Failed - Book not available";
        } else {
            int currentLoanId = loanCount;
            loanCount += 1;
            currentInventory.put(bookname, currentInventory.get(bookname) - 1);

            BookCheckout newCheckout = new BookCheckout(currentLoanId, username);
            currentCheckouts.get(bookname).add(newCheckout);

            responseMessage = "Your request has been approved, " + currentLoanId + " " + username + " " + bookname;
        }

        return responseMessage;
    }

    public synchronized String endLoan(int loanid) {
        String responseMessage = "";
        boolean foundMatchingCheckout = false;

        for (String bookname : currentCheckouts.keySet()) {
            List<BookCheckout> checkouts = currentCheckouts.get(bookname);
            for (BookCheckout checkout : checkouts) {
                if (checkout.loanid == loanid) {
                    foundMatchingCheckout = true;
                    checkouts.remove(checkout);
                    currentInventory.put(bookname, currentInventory.get(bookname) + 1);
                    break;
                }
            }
        }

        if (foundMatchingCheckout) {
            responseMessage = loanid + " is returned";
        } else {
            responseMessage = loanid + " not found, no such borrow record";
        }

        return responseMessage;
    }

    // NOTE: Doc does not say what order they should be printed?
    public synchronized List<String> getLoans(String username) {
        boolean foundMatchingCheckout = false;
        List<String> responseMessages = new ArrayList<>();

        for (String bookname : currentCheckouts.keySet()) {
            for (BookCheckout checkout : currentCheckouts.get(bookname)) {
                if (checkout.username.equals(username)) {
                    responseMessages.add(checkout.loanid + " " + bookname);
                    foundMatchingCheckout = true;
                }
            }
        }

        if (!foundMatchingCheckout) {
            responseMessages.add("No record found for " + username);
        }

        return responseMessages;
    }

    public synchronized List<String> getInventory() {
        List<String> responseMessages = new ArrayList<>();

        for (String bookname : currentInventory.keySet()) {
            responseMessages.add(bookname + " " + currentInventory.get(bookname));
        }

        return responseMessages;
    }

    public synchronized List<String> exit() {
        List<String> responseMessages = new ArrayList<>();

        try {
            String clientOutputFilename = "inventory.txt";
            FileWriter clientOutputFileWriter = new FileWriter(clientOutputFilename);
            PrintWriter clientOutputPrintWriter = new PrintWriter(clientOutputFileWriter);

            for (String bookname : currentInventory.keySet()) {
                // responseMessages.add(bookname + " " + currentInventory.get(bookname));
                clientOutputPrintWriter.println(bookname + " " + currentInventory.get(bookname));
            }

            clientOutputPrintWriter.close();
            clientOutputFileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return responseMessages;
    }

    public List<String> parseCommandFromClient(String command) {
        String[] tokens = command.split(" ");
        List<String> returnMessages = new ArrayList<>();

        // ------------------------------------------------------------------
        if (tokens[0].equals("set-mode")) {
            if (tokens[1].equals("u")) {
                returnMessages.add("The communication mode is set to UDP");
            }
            else if (tokens[1].equals("t")) {
                returnMessages.add("The communication mode is set to TCP");
            }
        // ------------------------------------------------------------------
        } else if (tokens[0].equals("begin-loan")) {
            int firstCharForTitle = command.indexOf("\"");
            int lastCharForTitle = command.lastIndexOf("\"");

            String username = tokens[1];
            String bookname = command.substring(firstCharForTitle, lastCharForTitle + 1);

            returnMessages.add(beginLoan(username, bookname));
        // ------------------------------------------------------------------
        } else if (tokens[0].equals("end-loan")) {
            int loanid = Integer.parseInt(tokens[1]);
            returnMessages.add(endLoan(loanid));
        // ------------------------------------------------------------------
        } else if (tokens[0].equals("get-loans")) {
            String username = tokens[1];
            returnMessages = getLoans(username);
        // ------------------------------------------------------------------
        } else if (tokens[0].equals("get-inventory")) {
            returnMessages = getInventory();
        // ------------------------------------------------------------------
        } else if (tokens[0].equals("exit")) {
            returnMessages = exit();
        // ------------------------------------------------------------------
        } else {
            System.out.println("ERROR: No such command");
        }
        
        return returnMessages;
    }

    // TCP Communication
    // NOTE: Didn't realize that <socket>.read() does not guarantee full messages,
    //       so switched to BufferedReader.
    // NOTE: Originally had raw socket reads and writes, so also switched the writes
    //       to the PrintWriter
    public void bidirectionalTCP(Socket tcpSocket) {
        String incomingCommand;
        List<String> responseMessages;

        // Client --> Server
        InputStreamReader isr;
        BufferedReader clientReader;

        // Server --> Client
        OutputStreamWriter osw;
        PrintWriter clientWriter;

        try {
            isr = new InputStreamReader(tcpSocket.getInputStream());
            clientReader = new BufferedReader(isr);

            osw = new OutputStreamWriter(tcpSocket.getOutputStream());
            clientWriter = new PrintWriter(osw, true);

            while ((incomingCommand = clientReader.readLine()) != null) {
                responseMessages = parseCommandFromClient(incomingCommand);

                for (String s : responseMessages) {
                    clientWriter.println(s);
                }
                // clientWriter.println("END_OF_MESSAGE");
                clientWriter.println();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                tcpSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void setupTCPConnection(int port) {
        // NOTE: Not sure that is was specified the max # of TCP connections, but the code
        //       did say clientId is between 1 and 9
        ExecutorService tcpThreadPool = Executors.newFixedThreadPool(10);
        try {
            ServerSocket server = new ServerSocket(port);

            while (true) {
                Socket singleClient = server.accept();
                tcpThreadPool.submit(() -> bidirectionalTCP(singleClient));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // UDP Communication
    public void sendUDP(InetAddress address, int port, String message) {
        byte[] sendBuffer = new byte[1024];
        DatagramSocket sendSocket;

        try {
            sendSocket = new DatagramSocket();
            DatagramPacket sendPacket;

            sendBuffer = message.getBytes();
            sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, address, port);
            
            sendSocket.send(sendPacket);
            sendSocket.close();
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void getUDP(int port) {
        DatagramPacket receivePacket;
        int len = 1024;

        try{
			DatagramSocket receiveSocket = new DatagramSocket(port);
			byte[] buf = new byte[len];

			while (true) {
				receivePacket = new DatagramPacket(buf, buf.length);
				receiveSocket.receive(receivePacket);

                String commandFromClient = new String(
                    receivePacket.getData(),
                    receivePacket.getOffset(),
                    receivePacket.getLength(),
                    StandardCharsets.UTF_8
                );

                List<String> responses = parseCommandFromClient(commandFromClient);
                String encodedResponse = String.join("%", responses);
                
                this.sendUDP(receivePacket.getAddress(), receivePacket.getPort(), encodedResponse);
            }
        } catch (SocketException e) {
			System.err.println(e);
		} catch (IOException e) {
			System.err.println(e);
		}
    }

    public static void main(String[] args) {
        int tcpPort;
        int udpPort;
        if (args.length != 1) {
            System.out.println("ERROR: Provide 1 argument: input file containing initial inventory");
            System.exit(-1);
        }
        String fileName = args[0];
        tcpPort = 7000;
        udpPort = 8000;

        // Parse the provided inventory file, and populate the inventory map
        try {
            Scanner inventoryScanner = new Scanner(new File(fileName));

            while (inventoryScanner.hasNextLine()) {
                String currentLine = inventoryScanner.nextLine();
                int firstCharForTitle = currentLine.indexOf("\"");
                int lastCharForTitle = currentLine.lastIndexOf("\"");

                // System.out.println(firstCharForTitle + " " + lastCharForTitle);
                String title = currentLine.substring(firstCharForTitle, lastCharForTitle + 1);
                int count = Integer.parseInt(currentLine.substring(lastCharForTitle + 1).trim());

                // System.out.println("-> " + title + " " + count);
                currentInventory.put(title, count);
                currentCheckouts.put(title, new ArrayList<>());
            }
            inventoryScanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        BookServer bs = new BookServer();
        ExecutorService initialThreadPool = Executors.newFixedThreadPool(2);

        initialThreadPool.submit(() -> {
            bs.getUDP(udpPort);
        });

        initialThreadPool.submit(() -> {
            bs.setupTCPConnection(tcpPort);
        });
    }
}