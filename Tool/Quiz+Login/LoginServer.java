import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.file.*;
import java.util.stream.Stream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

public class LoginServer {
  private static final int SERVER_PORT = 8080;
  private static Set<String> loggedInUsers = new HashSet<>();

  //ユーザー情報
  private static final String USERS_FILE = "users.txt";

  //問題と答えが格納されたテキストファイルのパス
  private static final String questionsFile = "questions.txt";
  private static final String answersFile = "answers.txt";

  //問題と答えのリスト
  private List<String> questions;
  private List<String> answers;

  //クライアントハンドラのリスト
  private List<ClientHandler> clients = new CopyOnWriteArrayList<>();

  //出題済み問題のインデックスリスト
  private List<Integer> usedQuestionIndices = new ArrayList<>();

  //現在の問題が進行中かを示すフラグ
  private AtomicBoolean questionInProgress = new AtomicBoolean(false);

  //クイズが開始されたか確認するフラグ
  private boolean quizStarted = false;

  //問題数を管理
  private int questioncount;

  // 回答済みのユーザー名を追跡するための集合
  Set<String> answeredUsers = new HashSet<>();
  // ユーザーごとのスコアを保持するマップ  
  Map<String, Integer> scores = new HashMap<>();



  public LoginServer() {
    try {
      questions = Files.readAllLines(Paths.get(questionsFile));
      answers = Files.readAllLines(Paths.get(answersFile));
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  public void start() {
    try {
      ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
      System.out.println("Server started on port " + SERVER_PORT);

      while (true) {
        Socket clientSocket = serverSocket.accept();
        
        System.out.println("Client connected: " + clientSocket);
        
        // Handle client connection in a new thread
        new Thread(() -> handleClient(clientSocket)).start();
        System.out.println("handleClient");

        //ハンドラを引き渡す
        ClientHandler client = new ClientHandler(clientSocket, this);
        clients.add(client);
        new Thread(client).start();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }  

  public static void main(String[] args) {
    LoginServer server = new LoginServer();
    server.start();

  }



  private void handleClient(Socket clientSocket) {
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
      PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

      String message;
      while ((message = in.readLine()) != null) {
        

        //ログインボタンが押された
        if (message.startsWith("LOGIN_c")) {
          System.out.println("pushed loginbutton");

          String[] parts = message.split(" ");

          //ユーザー名とパスワードがきちんと入力されているとき
          if (parts.length == 3) {
            String username = parts[1];
            String password = parts[2];

            //ユーザーリストに登録があるとき
            if (verifyPassword(username, password)) {

              //ログイン済みのときはログインできない
              if (loggedInUsers.contains(username)) {
                System.out.println("already login");
                out.println("FAILURE_s");

              //ログインしたユーザーを記録
              } else {
                System.out.println("success login");
                loggedInUsers.add(username);

                out.println("SUCCESS_s " + username);

                // クイズ開始フラグを設定
                quizStarted = true;
                System.out.println("quizStarted true");

              }

            //ユーザーリストに名前がないとき
            } else {
              System.out.println("not register");
              out.println("FAILURE_s");
            }
          //されていないとき
          } else {
            System.out.println("not correct input");
            out.println("FAILURE_s");
          }

        //登録ボタンが押された
        }else if(message.startsWith("REGISTER_c")){
          System.out.println("pushed registrationbutton");
          String[] parts = message.split(" ");

          //ユーザー名とパスワードがきちんと入力されているとき
          if (parts.length == 3 && !parts[1].contains(",") && !parts[2].contains(",")){
            String username = parts[1];
            String password = parts[2];

            //ユーザーリストから名前の重複をチェック
            if (userExists(username)) {
              System.out.println("dup registration");
              out.println("REG_FAILURE_s");

            //重複していないとき
            } else {
              // ユーザーを登録
              System.out.println("correct registration");
              try (FileWriter writer = new FileWriter(USERS_FILE, true)) {

                //ユーザー情報を書き込む
                writer.write(username + "," + password + "," + 0 + System.lineSeparator());
                out.println("REG_SUCCESS_s");

              } catch (IOException e) {
                System.out.println("failure registration");
                out.println("REG_FAILURE_s");
              }
            }
          //されていないとき
          } else {
            System.out.println("not correct input");
            out.println("REG_CANNOTUSE_s");
          }
        }
      }

      clientSocket.close();
      System.out.println("Client disconnected: " + clientSocket);
    } catch (IOException e) {

      System.out.println("error handleClient");
      e.printStackTrace();
    }
  }


  //パスワード照合
  private static boolean verifyPassword(String loginusername, String loginpassword) {
    
    System.out.println("verifyPassword");
      
    try (Stream<String> lines = Files.lines(Paths.get(USERS_FILE))) {
      
          return lines.anyMatch(line -> line.startsWith(loginusername + "," + loginpassword+","));
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

  //ランダムな問題をクライアントに送信するメソッド
  public void sendRandomQuestion() {
    System.out.println("sendRandomQuestion");

    // クイズが開始されていない場合は終了
    if (!quizStarted) {
      return;
    }

    //全ての問題を使ってしまったら。使用履歴をクリア
    if (usedQuestionIndices.size() == questions.size()) {
      usedQuestionIndices.clear();
    }

    //出題される問題を事前に指定された問題リストからランダムに選択
    int questionIndex;
    do {
      questionIndex = ThreadLocalRandom.current().nextInt(questions.size());
    } while (usedQuestionIndices.contains(questionIndex));
    usedQuestionIndices.add(questionIndex);
    String question = questions.get(questionIndex);
    String answer = answers.get(questionIndex);

    //問題数管理
    questioncount++;
    System.out.println("quizcount " + questioncount);

    //問題と答えは、ClientHandlerインスタンスを保持する各クライアントに送信
    for (ClientHandler client : clients) {
      client.setAnswer(answer);
      client.sendMessage("QUESTION_s " +questioncount+" "+ question);
    }

    //問題を進行中に変更
    questionInProgress.set(true);

    
  }

    //正答受信後、全クライアントに処理を行うメソッド
    public void handleAnswerReceived() {
      System.out.println("handleAnswerReceived");
      //質問が進行中の時、停止して回答をnullにする
      if (questionInProgress.compareAndSet(true, false)) {
        for (ClientHandler client : clients) {
          client.setAnswer(null);
        }

        //解答済みユーザーのリセット
        answeredUsers.clear();
  
        //3秒間のスリープをしクライアントに回答を送信する前に一定の待機時間を確保
        try {
          Thread.sleep(3000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
  
        //次のランダムな質問を生成し、全てのクライアントに送信

        //5問で終了してファイナルスコアを送信
        ///終了処理
        if (questioncount>=5){
          
          writetotalscore();
          finalscore();

          
          return;
        }else{
          sendRandomQuestion();
        }
      }
    }

    //ファイナルスコアを送信
    public void finalscore() {
      StringBuilder sb = new StringBuilder();

      for (Map.Entry<String, Integer> entry : scores.entrySet()) {
        String key = entry.getKey();
        Integer value = entry.getValue();
        sb.append("username: ").append(key).append(", finalscore: ").append(value).append("\n");
      }
    
      String result = sb.toString();
      System.out.println(result);

        //クライアント全員に最終得点を公開
        for (ClientHandler client : clients) {
          client.out.println("FINALSCORE_s " + result.replace("\n", "\\n"));
        }
    }

    //トータルスコアの書き込み
    public void writetotalscore(){

      System.out.println("totalscore writing");

      try (BufferedReader br = new BufferedReader(new FileReader("users.txt"))) {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            // 各行のデータを適切に分割・処理する
            String[] data = line.split(","); // 適切な区切り文字に置き換える
            String username = data[0]; // ユーザー名
            int totalScore = Integer.parseInt(data[2]); // トータル得点

            //最終スコアをハッシュマップから取得
            int finalScore = scores.getOrDefault(username, 0);

            data[2] = String.valueOf(totalScore+finalScore);

            // linesリストに修正後の行を追加する
            String updatedLine = String.join(",", data); // 適切な区切り文字に置き換える
            lines.add(updatedLine);
        }

        // users.txtファイルの内容を更新する
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("users.txt"))) {
          for (String updatedLine : lines) {
              bw.write(updatedLine);
              bw.newLine();
          }
        }

      } catch (IOException e) {
          // ファイル読み込みエラーのハンドリング
          e.printStackTrace();
      }
    }

    //トータルスコアの取得
    public int getScoreFromTextFile(String username) {
      int score = 0;
      
      try (BufferedReader br = new BufferedReader(new FileReader("users.txt"))) {
          String line;
          while ((line = br.readLine()) != null) {
              String[] data = line.split(","); // Assuming the data is comma-separated
              String fileUsername = data[0];
              int fileScore = Integer.parseInt(data[2]);
  
              if (fileUsername.equals(username)) {
                  score = fileScore;
                  break;
              }
          }
      } catch (IOException e) {
          // Handle file reading error
          e.printStackTrace();
      }
      
      return score;
  }


  //クライアントを削除
  public void removeClient(ClientHandler client) {
    System.out.println("removeClient");
    clients.remove(client);
  }

  //クライアントとの通信を担当するクラス
  private class ClientHandler implements Runnable {
    private Socket socket;
    private LoginServer server;
    private BufferedReader in;
    private PrintWriter out;
    private String answer;

    public ClientHandler(Socket socket, LoginServer server) throws IOException {
      this.socket = socket;
      this.server = server;
      in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      out = new PrintWriter(socket.getOutputStream(), true);
    }

    @Override
    public void run() {
      try {
        String message;
        while ((message = in.readLine()) != null) {
          System.out.println("ClientHandler");
          handleMessage(message);

          //クイズが開始されていない場合は待機
          if (!quizStarted) {
            try {
              Thread.sleep(1000); // 1秒待機
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            continue;
          }
          
        }
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        server.removeClient(this);
        try {
          socket.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    //受信したメッセージの処理を行うメソッド
    private void handleMessage(String message) {
      
      //メッセージが"ANSWER_c"で始まる場合、回答として扱われます
      if (message.startsWith("ANSWER_c ")) {
        String[] parts = message.split(" ");
        String yourusername = parts[1];

        String[] answerParts = Arrays.copyOfRange(parts, 2, parts.length);
        String youranswer = String.join(" ", answerParts);


        System.out.println("receive answer");

        handleAnswer(youranswer,yourusername);

        //メッセージが"START_c"で始まる場合、クライアントがゲームを開始する意図を示しており、サーバーが問題を送信します。
      } else if (message.startsWith("START_c")) {
        System.out.println("receive start");
        
        //質問が進行中でない場合、問題を送信する
        if (!questionInProgress.get()) {

          System.out.println("sendRandomQuestion");
          server.sendRandomQuestion();
        }

      }else if(message.startsWith("MYTOTALSCORE_c ")){

        
        String[] parts = message.split(" ");
        String username = parts[1];

        System.out.println("sendNextSignal receive "+username);

        sendtotalscore(getScoreFromTextFile(username));

      }
    }

    

    //クライアントからの回答の正誤判定をする
    private void handleAnswer(String clientAnswer,String clientusername) {
      if (questionInProgress.get() && answer != null) {
        //正解の場合
        if (clientAnswer.equalsIgnoreCase(answer)) {
          //正解したクライアントのみに得点を与える
          
          System.out.println("correct answer received");

          // スコアの処理を追加
          if (!answeredUsers.contains(clientusername)) {
            int score = scores.getOrDefault(clientusername, 0);
            score += 1;
            scores.put(clientusername, score);
            answeredUsers.add(clientusername);
            System.out.println("Score updated for user: " + clientusername + ", new score: " + score);

            out.println("CORRECT_s " + score);
          }

          //回答を送信
          for (ClientHandler client : clients) {
                    client.out.println("ANSWER_s " + clientusername  + " "+ client.answer);
          }
        
        //次の問題に進むための処理を全クライアントに行う
        server.handleAnswerReceived();

        }else {
          System.out.println("wrong answer");

          //間違いのメッセージを表示する
          out.println("WRONG_s ");

        }
        
      }
    }

    //トータルスコアをクライアントに送信
    public void sendtotalscore(int yourtotalscore){
      System.out.println("your totalscore send");
      out.println("YOURTOTALSCORE_s "+yourtotalscore);
    }
    
    public void sendMessage(String message) {
      out.println(message);
    }

    public void setAnswer(String answer) {
      this.answer = answer;
    }
    
  }

  
}


