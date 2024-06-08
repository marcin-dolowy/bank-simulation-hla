package queue_manager;

public class Queue {
    private int id;
    private java.util.Queue<Integer> queue;

    public Queue(int id) {
        this.id = id;
    }

    public java.util.Queue<Integer> getQueue() {
        return queue;
    }
}
