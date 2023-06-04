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
        if(this.username == null) n.rank = this.rank;
        else n.rank = this.rank+1;
        }
        if(this.right == null) this.right = n;
        else this.right.add_leaf(n,p);
    }
    if(n.score == this.score){
        n.rank = this.rank;
        if(this.username == null && this.right != null && this.samescore == null){this.right.rank += 1;this.right.rightPlus();}
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
            if(this.right != null) {this.right.rank +=1;
            this.right.rightPlus();}
            if(this.username==null && this.samescore != null){this.samescore.rank+=1;this.samescore.rightPlus();}
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

public class Test2{
    public static void main(String[] main){
        Node head = new Node(null,3);
        
        head.add_leaf(new Node("a",4),head.existSameScore(2));
        head.add_leaf(new Node("b",1),head.existSameScore(1));
        /*
        head.add_leaf(new Node("a",1),head.existSameScore(1));
        head.add_leaf(new Node("b",4),head.existSameScore(4));
        head.add_leaf(new Node("c",2),head.existSameScore(2));
        head.add_leaf(new Node("d",5),head.existSameScore(5));
       // head.add_leaf(new Node("e",3),head.existSameScore(3));
        head.add_leaf(new Node("f",2),head.existSameScore(2));
       // head.add_leaf(new Node("i",3),head.existSameScore(3));
        head.add_leaf(new Node("g",0),head.existSameScore(0));
        head.add_leaf(new Node("h",4),head.existSameScore(4));
        */
        head.showInfo();
    }
}