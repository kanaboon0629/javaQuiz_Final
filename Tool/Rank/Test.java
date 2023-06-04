import java.io.*;

class Node{
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
    if(this.right != null) this.right.rank += 1;
    if(this.left != null) this.left.rank += 1;  
  }

  public void add_leaf(Node n){
    if( this.score<n.score ){
      if(this.username == null && this.right == null){
        n.rank = this.rank;this.right = n;
      }else{
      if(this.right == null) {n.rank = this.rank+1;this.right = n;}
      else {
        if(this.username==null){n.rank = this.rank;this.right.add_leaf(n);}
        else{n.rank = this.rank;System.out.println("calledas");this.right.add_leaf(n);}
      }
      }
    } 
    if(n.score == this.score){
      if(this.username == null && this.samescore == null){
        n.rank = this.rank -1;this.samescore = n;
      }else{
      if(this.samescore == null) {n.rank = this.rank;this.samescore = n;}
      else this.samescore.add_leaf(n);
      }
    }
    if(n.score < this.score ){
      //headに対しては特別にrankをインクリメントする
      if((this.left == null) && (this.username == null)){this.rank += 1; this.left = n;if(this.right!=null) this.right.score +=1;
      }else{
      if(this.left == null) {this.left = n;}
      else {
        if(this.username==null){this.rank+=1;if(this.right != null) {this.right.rank+=1;this.rank = this.right.rank;};this.right.rightPlus();this.left.add_leaf(n);}
        else{this.rank+=1;this.right.rightPlus();this.left.add_leaf(n);}
      }
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

public class Test{
    public static void main(String[] main){
        Node head = new Node(null,3);
        System.out.println("head"+head.rank+"showinfo");
        head.add_leaf(new Node("a",1));
        System.out.println("head"+head.rank+"showinfo");
        head.showInfo();
        System.out.println("head"+head.rank+"showinfo");
        head.add_leaf(new Node("b",4));
        System.out.println("head"+head.rank+"showinfo");
        head.showInfo();
        System.out.println("head"+head.rank+"showinfo");
        head.add_leaf(new Node("c",2));
        System.out.println("head"+head.rank+"showinfo");
        head.showInfo();
        System.out.println("head"+head.rank+"showinfo");
        head.add_leaf(new Node("d",5));
        System.out.println("head"+head.rank+"showinfo");
        head.showInfo();
        System.out.println("head"+head.rank+"showinfo");
        head.add_leaf(new Node("e",3));
        System.out.println("head"+head.rank+"showinfo");
        head.showInfo();
        System.out.println("head"+head.rank+"showinfo");
        head.add_leaf(new Node("f",2));
        System.out.println("head"+head.rank+"showinfo");
        head.showInfo();
        System.out.println("head"+head.rank+"showinfo");
        head.add_leaf(new Node("g",0));
        System.out.println("head"+head.rank+"showinfo");
        head.showInfo();
        System.out.println("head"+head.rank+"showinfo");
        head.add_leaf(new Node("h",4));
        System.out.println("head"+head.rank+"showinfo");
        head.showInfo();
    }
}