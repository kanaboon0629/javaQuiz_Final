import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.file.*;

import java.util.stream.Stream;


public class LoginServer {
  private static final int SERVER_PORT = 8080;
  private static Set<String> loggedInUsers = new HashSet<>();

  //ユーザー情報
  private static final String USERS_FILE = "users.txt";

  public static void main(String[] args) {
    try {
      ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
      System.out.println("Server started on port " + SERVER_PORT);

      while (true) {
        Socket clientSocket = serverSocket.accept();
        System.out.println("Client connected: " + clientSocket);

        // Handle client connection in a new thread
        new Thread(() -> handleClient(clientSocket)).start();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void handleClient(Socket clientSocket) {
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
      PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

      String message;
      while ((message = in.readLine()) != null) {

        //ログインボタンが押された
        if (message.startsWith("LOGIN_c")) {
          String[] parts = message.split(" ");

          //ユーザー名とパスワードがきちんと入力されているとき
          if (parts.length == 3) {
            String username = parts[1];
            String password = parts[2];

            //ユーザーリストに登録があるとき
            if (verifyPassword(username, password)) {

              //ログイン済みのときはログインできない
              if (loggedInUsers.contains(username)) {
                out.println("FAILURE_s");

              //ログインしたユーザーを記録
              } else {
                loggedInUsers.add(username);
                out.println("SUCCESS_s");             
              }

            //ユーザーリストに名前がないとき
            } else {
              out.println("FAILURE_s");
            }
          //されていないとき
          } else {
            out.println("FAILURE_s");
          }

        //登録ボタンが押された
        }else if(message.startsWith("REGISTER_c")){
          String[] parts = message.split(" ");

          //ユーザー名とパスワードがきちんと入力されているとき
          if (parts.length == 3&&
            !parts[1].contains(",") &&
            !parts[2].contains(",")) {
            String username = parts[1];
            String password = parts[2];

            //ユーザーリストから名前の重複をチェック
            if (userExists(username)) {
              out.println("REG_FAILURE_s");

            //重複していないとき
            } else {
              // ユーザーを登録
              try (FileWriter writer = new FileWriter(USERS_FILE, true)) {
                writer.write(username + "," + password + System.lineSeparator());
                out.println("REG_SUCCESS_s");
              } catch (IOException e) {
                out.println("REG_FAILURE_s");
              }
            }
          //されていないとき
          } else {
            out.println("REG_FAILURE_s");
          }

        }
      }

      clientSocket.close();
      System.out.println("Client disconnected: " + clientSocket);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  //パスワード照合
  private static boolean verifyPassword(String loginusername, String loginpassword) {
    
    System.out.println("verifyPassword");
      
    try (Stream<String> lines = Files.lines(Paths.get(USERS_FILE))) {
          return lines.anyMatch(line -> line.equals(loginusername + "," + loginpassword));
    } catch (IOException e) {
      return false;
    }
  }

  //ユーザー登録されてるか確認
  private static boolean userExists(String username) {
    System.out.println("userExists");
    try (Stream<String> lines = Files.lines(Paths.get(USERS_FILE))) {
        return lines.anyMatch(line -> line.startsWith(username + ","));
    } catch (IOException e) {
        return false;
    }
  }
}


