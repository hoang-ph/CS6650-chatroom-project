package server;

import java.awt.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import client.Client;
import gui.ChatroomServerGUI;
import gui.ClientGUI;

public class ChatroomServer {

  public int ID;
  public String chatroomName;
  public Client hostClient;
  public ChatroomServerGUI chatroomServerGUI;
  public ServerSocket serverSocketForClients; // for receiving messages from clients in chatroom.
  public int portForClients;
  public InetAddress group;
  public DatagramSocket datagramSocketForMulticast;
  public byte[] buffer;

  // heartbeat vars for keeping in contact with LookUpServer
  public String heartbeatAddress;
  public int heartbeatPort;
  public Socket heartbeatSocket;
  public BufferedReader heartbeatReader;
  public BufferedWriter heartbeatWriter;
  public Socket socketForLookUpServer; // TODO - for receiving heartbeats from LookUpServer.

  public ChatroomServer(int ID, Client hostClient, String groupIP, String chatroomName,
                        String heartbeatAddress, int heartbeatPort) {
    this.ID = ID;
    this.chatroomName = chatroomName;
    this.hostClient = hostClient;
    this.chatroomServerGUI = new ChatroomServerGUI(this);
    this.heartbeatAddress = heartbeatAddress;
    this.heartbeatPort = heartbeatPort;
    try {
      this.group = InetAddress.getByName(groupIP);
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    try {
      this.serverSocketForClients = new ServerSocket(0);
      this.portForClients = this.serverSocketForClients.getLocalPort();
      NewUserConnector newUserConnector = new NewUserConnector();
      new Thread(newUserConnector).start();
    } catch (IOException e) {
      e.printStackTrace();
    }
    try {
      this.heartbeatSocket = new Socket(heartbeatAddress, heartbeatPort);
      HeartbeatHandler heartbeatHandler = new HeartbeatHandler(this.heartbeatSocket);
      new Thread(heartbeatHandler).start();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public class NewUserConnector implements Runnable {
    @Override
    public void run() {
      while (true) {
        Socket clientSocket = null;
        try {
          clientSocket = serverSocketForClients.accept();
        } catch (IOException e) {
          e.printStackTrace();
        }
        ClientSocketHandler clientSocketHandler = new ClientSocketHandler(clientSocket);
        new Thread(clientSocketHandler).start();
      }
    }
  }

  public class HeartbeatHandler implements Runnable {

    public Socket socket;

    public HeartbeatHandler(Socket socket) {
      this.socket = socket;
    }

    @Override
    public void run() {
      try {
        heartbeatReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        heartbeatWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
      } catch (IOException e) {
        e.printStackTrace();
      }
      while (true) {
        try {
          String line = heartbeatReader.readLine();
          String[] messageArray = line.split("@#@");
          if (messageArray[0].equalsIgnoreCase("heartbeat")) {
            // don't do anything since the LookUp server knows if this message didn't go through
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public void multicastMessage(String message) {
    try {
      datagramSocketForMulticast = new DatagramSocket();
      message = "chatkey125" + message;
      buffer = message.getBytes();
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, 4446);
      datagramSocketForMulticast.send(packet);
      datagramSocketForMulticast.close();
    } catch (SocketException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  private class ClientSocketHandler implements Runnable {
    private final Socket clientSocket;
    private final InetAddress clientAddress;
    private final int clientPort;

    public ClientSocketHandler(Socket socket) {
      this.clientSocket = socket;
      this.clientAddress = this.clientSocket.getInetAddress();
      this.clientPort = this.clientSocket.getPort();
    }

    private String handleMessage(String message) {
      // multicast to all connected clients
      multicastMessage(message);
      String[] fullMessage = message.split("@#@");
      String sender = fullMessage[0];
      String actualMessage = fullMessage[1];
      chatroomServerGUI.displayNewMessage(sender, actualMessage);
      try {
        heartbeatWriter.write("messageSent@#@" + sender + "@#@" + actualMessage);
        heartbeatWriter.newLine();
        heartbeatWriter.flush();
      } catch (IOException e) {
        e.printStackTrace();
      }
      return "success";
      // TODO - notify LookUpServer of the message sent

    }

    public void run() {
      BufferedReader reader = null;
      BufferedWriter writer = null;
      try {
        reader = new BufferedReader(
                new InputStreamReader(this.clientSocket.getInputStream()));
        writer = new BufferedWriter(
                new OutputStreamWriter(this.clientSocket.getOutputStream()));
      } catch (IOException e) {
        e.printStackTrace();
      }
      while (true) {
        try {
          String line = reader.readLine();
          if (line == null) {
            continue;
          }
          String beginningSubstring = line.substring(0, 7);
//          String[] messageArray = line.split(" ");
          String response = null;
          if (beginningSubstring.equalsIgnoreCase("message")) {
            response = handleMessage(line.substring(7));
          } else {
            response = "invalidRequestType";
          }
//          writer.write(response);
//          writer.newLine();
//          writer.flush();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }
}
