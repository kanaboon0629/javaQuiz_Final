import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.file.*;
import java.util.stream.Stream;
import java.util.concurrent.*;
import java.nio.channels.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.charset.StandardCharsets;

public class MergeServer {
  private static final int SERVER_PORT = 8080;
  private DatagramSocket udpSocket;
  private static final int SERVER_UDP_PORT = 8081;
  private ExecutorService udpExecutorService;

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

  private ScheduledExecutorService questionTimer;

  private Map<ClientHandler, String> clientUsernames = new ConcurrentHashMap<>();

  //問題数を管理
  private int questioncount;

  // ユーザーごとのスコアを保持するマップ  
  Map<String, Integer> scores = new HashMap<>();

  private Runtime runtime ;
  private long usedMemory ;

  public MergeServer() {
    try {
      questions = Files.readAllLines(Paths.get(questionsFile));
      answers = Files.readAllLines(Paths.get(answersFile));
      this.questionTimer = Executors.newSingleThreadScheduledExecutor();
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
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }  

  public static void main(String[] args) {
    MergeServer server = new MergeServer();
    server.start();
  }

  private void handleClient(Socket clientSocket) {
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
      PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

      String message;
      while ((message = in.readLine()) != null) {
        System.out.println("message at handleClient = "+message);

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

                //ハンドラを引き渡す
                ClientHandler client = new ClientHandler(clientSocket, this);
                clients.add(client);
                System.out.println("quiz server start");
                client.name = username;

                try{
                  String message2;
                  while ((message2 = in.readLine()) != null) {
                     System.out.println("message at ClientHandler = "+message2);
                    //System.out.println("ClientHandler");
                     client.handleMessage(message2);     
                 }
                 }catch (IOException e) {
                   e.printStackTrace();
                  }finally {
                   removeClient(client);
                  try{ 
                      client.socket.close();
                    } catch (IOException e) {
                    e.printStackTrace();
                   }
                } 
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
          if (parts.length == 3&& !parts[1].contains(",") && !parts[2].contains(",")) {
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
            out.println("REG_FAILURE_s");
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
  public void sendRandomQuestion(){
    //addded
    questionTimer.shutdownNow();
    System.out.println("sendRandomQuestion");
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
      client.sendMessage("QUESTION_s " + question);
    }

    //問題を進行中に変更
    questionInProgress.set(true);
    questionTimer = Executors.newSingleThreadScheduledExecutor();
    questionTimer.schedule(this::handleAnswerReceived, 20, TimeUnit.SECONDS);
  }
  

    //正答受信後、全クライアントに処理を行うメソッド
    public void handleAnswerReceived() {
      System.out.println("handleAnswerReceived");
      //質問が進行中の時、停止して回答をnullにする
      if (questionInProgress.compareAndSet(true, false)) {
        for (ClientHandler client : clients) {
          client.setAnswer(null);
        }
  
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
        }
          sendRandomQuestion();
        
      }
      
    }
  
  public void sendChat(String nam,String mes){
    for(ClientHandler client:clients){
      System.out.println("sent message to " + client.name);
      String messagetoSend ="CHAT_s "+nam+": "+mes;
      client.out.println(messagetoSend);
      runtime= Runtime.getRuntime();
      usedMemory = runtime.totalMemory() - runtime.freeMemory();
      System.out.println("使用中のメモリ: " + usedMemory + " bytes");
    }
  }

  //クライアントを削除
  public void removeClient(ClientHandler client) {
    System.out.println("removeClient");
    clients.remove(client);
  }
  
  ///トータルスコアの書き込み
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


//後藤
private class Node{
  String username;
  int score;
  int rank;
  Node right;
  Node samescore;
  Node left;
  
  public Node(String username, int score){
    this.username = username;
    this.score = score;
    this.rank = 1;
    this.right = null;
    this.samescore = null;
    this.left =  null;
  }

  public void rightPlus(){
    if(this.right != null) {this.right.rank += 1;this.right.rightPlus();}
    if(this.samescore != null) {this.samescore.rank += 1;this.samescore.rightPlus();}
    if(this.left != null) {this.left.rank += 1;this.left.rightPlus();}
  }


  public boolean existSameScore(int num){
    if(this.score == num) return true;
    if(this.right != null && this.left != null){ return (this.right.existSameScore(num)||this.left.existSameScore(num));} 
    else if(this.right != null) return this.right.existSameScore(num);
    else if(this.left != null) return this.left.existSameScore(num);
    else if(this.score == num) return true;
    else return false;
  }

 public void add_leaf(Node n,boolean p){
    if( this.score>n.score ){
        if(!p){
          if(this.username.equals("head") && this.samescore == null) n.rank = this.rank;
          else n.rank = this.rank+1;
        }
        if(this.right == null) this.right = n;
        else this.right.add_leaf(n,p);
    }
    if(n.score == this.score){
        n.rank = this.rank;
        if(this.username.equals("head") && this.right != null && this.samescore == null){this.right.rank += 1;this.right.rightPlus();}
        if(this.samescore == null) this.samescore = n;
        else this.samescore.add_leaf(n,p);
    }
    if(n.score > this.score ){
        if(!p)this.rank+=1;
        if(this.left == null) {
            if(this.right != null) {
            if(!p) this.right.rank +=1;
            this.right.rightPlus();}
            this.left = n;
        }else {
            if(!p){
            if(this.right != null) {this.right.rank +=1;this.right.rightPlus();}
            if((!this.username.equals("head"))&&this.samescore != null)
            if(this.username.equals("head") && this.samescore != null){this.samescore.rank+=1;this.samescore.rightPlus();}
            }
            this.left.add_leaf(n,p);
        }
    }
  }

  public void showInfo(){
    if(this.left != null) this.left.showInfo();
    if(this.samescore != null) this.samescore.showInfo();
    if(!(this.username==null))System.out.println(this.rank+this.username+this.score);
    if(this.right != null) this.right.showInfo();
  }
}

  //クライアントとの通信を担当するクラス
  private class ClientHandler {
    private Socket socket;
    private MergeServer server;
    private BufferedReader in;
    private PrintWriter out;
    private String answer;
    private Node head;
    private int score;
    private int rank;
    private String name;

    public ClientHandler(Socket socket, MergeServer server) throws IOException {
      this.socket = socket;
      this.server = server;
      in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      out = new PrintWriter(socket.getOutputStream(), true);
      this.score = 0;
      this.rank = 0;
    }
  public void yourRank(Node n,int num){
    if(n.score == num) out.println("TOTALSCORE_s "+"your rank is "+n.rank);
    else{
    if(n.left != null) yourRank(n.left, num);
    if(n.samescore != null) yourRank(n.samescore, num);
    if(n.right != null) yourRank(n.right, num);
    }
  }
  public void listUser(Node n){
    if(n.left != null) listUser(n.left);
    if(n.samescore != null) listUser(n.samescore);
    if(!n.username.equals("head")) out.println("TOTALSCORE_s "+n.rank + " : " +n.username+ " " + n.score);
    if(n.right != null) listUser(n.right);

  }
  public void winner(Node n){

    if(n.username.equals("head")&&n.left == null && n.samescore == null){
      winner(n.right);
    }else{
    if(n.rank == 1) {
      if(n.samescore != null) winner(n.samescore); 
      if(!n.username.equals("head"))out.println("TOTALSCORE_s "+"the winner is "+n.username);
    }else{
    if(n.left!=null) winner(n.left);
    if(n.samescore != null) winner(n.samescore);
    if(n.right!=null) winner(n.right);
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
      }else if (message.equals("RAISEHAND_c")) {
        for (ClientHandler client : clients) {
          client.out.println("PAUSE_s");
        }
      }
      else if (message.startsWith("USERNAME_c ")) {
        name = message.substring(11);
        handleUsername(message.substring(11));
      }else if(message.startsWith("CHAT_c")){
        System.out.println("received message:" + message);
        String mes = message.substring(6);
        server.sendChat(name,mes);
      }else if(message.startsWith("END_c")) {
        System.exit(1);

      }else if (message.startsWith("FINALSCORE_c ")){
        System.out.println("finalscore received");

        String[] parts = message.split(" ");

 


        this.head = new Node("head",-1);
        this.name = parts[1];
        this.score = Integer.parseInt(parts[2]);
        try{Thread.sleep(10);}catch(InterruptedException ie){}
        for(ClientHandler client:server.clients){
          this.head.add_leaf(new Node(client.name,client.score),head.existSameScore(client.score));
        }

        this.yourRank(head,this.score);
        this.winner(head);
        this.listUser(head);
        System.out.println("head's rank = "+ head.rank);


        String finalusername = parts[1];

        int finalscore = Integer.parseInt(parts[2]);

        ///ここでトータルスコア送信したいけどファイルはいるとバグる
        /*
        //finalscoreをユーザー情報に登録
        try (BufferedReader reader = new BufferedReader(new FileReader(USERS_FILE))) {
          List<String> lines = new ArrayList<>();
      
          String line;
          while ((line = reader.readLine()) != null) {
              String[] userparts = line.split(",");
              if (userparts.length >= 3 && userparts[0].equals(finalusername)) {
                  int TotalScore = Integer.parseInt(userparts[2]);
                  TotalScore += finalscore;
                  //クライアントトータルスコアを伝える
                  out.println("TOTALSCORE_s " +"your total score is "+TotalScore);
                  userparts[2] = Integer.toString(TotalScore);
                  line = String.join(",", userparts);
              }
              lines.add(line);
          }
          
          // Write the updated lines to the file
          try (BufferedWriter writer = new BufferedWriter(new FileWriter(USERS_FILE))) {
              for (String updatedLine : lines) {
                  writer.write(updatedLine);
                  writer.newLine();
              }
          } catch (IOException e) {
              e.printStackTrace();
          }
          
      } catch (IOException e) {
          e.printStackTrace();
      }

      */
      }
    }

    private void handleUsername(String username) {
      clientUsernames.put(this, username);
    }

    //クライアントからの回答の正誤判定をする
    private void handleAnswer(String clientAnswer,String clientusername) {
      //正解の場合
      if (answer != null && clientAnswer.equalsIgnoreCase(answer)) {
        System.out.println("correct answer");


        
        /// スコアの処理を追加
          int score = scores.getOrDefault(clientusername, 0);
          score += 1;
          scores.put(clientusername, score);
          System.out.println("Score updated for user: " + clientusername + ", new score: " + score);

          out.println("CORRECT_s " + score);

        //回答を送信
        for (ClientHandler client : clients) {
            client.out.println("ANSWER_s " + clientusername  + " "+ client.answer);
        }

        //次の問題に進むための処理を全クライアントに行う
        server.handleAnswerReceived();
        questionTimer.shutdownNow(); // タイマーのリセット
        questionTimer = Executors.newSingleThreadScheduledExecutor(); // タイマーの再初期化
        //server.sendRandomQuestion(); // 新しい質問の開始
      } else {
        System.out.println("wrong answer");
        //間違いのメッセージを表示する
        out.println("WRONG_s ");
      }
    }
    
    public void sendMessage(String message) {
      out.println(message);
    }

    public void setAnswer(String answer) {
      this.answer = answer;
    }
  }
}


