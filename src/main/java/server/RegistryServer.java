package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class RegistryServer {

  ServerSocket serverSocket;
  public List paxosServerInfos;
  public int myPort;
  public String myAddress;

  public RegistryServer() {
    try {
      this.paxosServerInfos = Collections.synchronizedList(new ArrayList());
      this.serverSocket = new ServerSocket(0);
      this.myPort = this.serverSocket.getLocalPort();
      this.myAddress = this.serverSocket.getInetAddress().getHostAddress();
      new Thread(new NewServerWaiter()).start();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private class NewServerWaiter implements Runnable {
    @Override
    public void run() {
      while (true) {
        Socket newServerSocket = null;
        try {
          newServerSocket = serverSocket.accept();
          NewServerSocketHandler newServerSocketHandler = new NewServerSocketHandler(newServerSocket);
          new Thread(newServerSocketHandler).start();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public String getRegistryAddress() {
    return this.myAddress;
  }

  public int getRegistryPort() {
    return this.myPort;
  }

  private class NewServerSocketHandler implements Runnable {
    private final Socket newServerSocket;
    private String newServerAddress;
    private int newServerPort;
    private String newServerPaxosRole;
    private BufferedReader newServerReader;
    private BufferedWriter newServerWriter;

    public NewServerSocketHandler(Socket newServerSocket) {
      this.newServerSocket = newServerSocket;
    }

    public void run() {
      try {
        this.newServerReader = new BufferedReader(
                new InputStreamReader(this.newServerSocket.getInputStream()));
        this.newServerWriter = new BufferedWriter(
                new OutputStreamWriter(this.newServerSocket.getOutputStream()));
        PaxosServerInfo newPaxosServerInfo = new PaxosServerInfo(this.newServerAddress,
                this.newServerPort, this.newServerSocket, this.newServerWriter);
//        paxosServerInfos.add(newPaxosServerInfo);
//        System.out.println("Registry server accepted new server connection");
        String line = this.newServerReader.readLine();
        String[] messageArray = line.split("@#@");
        this.newServerAddress = messageArray[0];
        this.newServerPort = Integer.parseInt(messageArray[1]);
        this.newServerPaxosRole = messageArray[2];
        // Tell each server that has already connected to make a socket connection to the new server
        synchronized (paxosServerInfos) {
          Iterator i = paxosServerInfos.iterator();
          while (i.hasNext()) {
            PaxosServerInfo info = (PaxosServerInfo) i.next();
            info.writer.write("startConnection@#@" + this.newServerAddress + "@#@" + this.newServerPort + "@#@" + this.newServerPaxosRole);
            info.writer.newLine();
            info.writer.flush();
          }
        }
        paxosServerInfos.add(newPaxosServerInfo);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public class PaxosServerInfo {
    public Socket registryPaxosServer;
    public String addressForPaxosServers;
    public int portForPaxosServers;
    public BufferedWriter writer;

    public PaxosServerInfo(String addressForPaxosServers, int portForPaxosServers, Socket registryPaxosServer,
                       BufferedWriter writer) {
      this.addressForPaxosServers = addressForPaxosServers;
      this.portForPaxosServers = portForPaxosServers;
      this.registryPaxosServer = registryPaxosServer;
      this.writer = writer;
    }
  }
}
