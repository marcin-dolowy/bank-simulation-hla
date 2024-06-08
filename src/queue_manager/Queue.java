package queue_manager;

import java.util.ArrayList;
import java.util.List;

public class Queue {
    private int id;
    private List<Integer> queue = new ArrayList<>();

    public Queue(int id) {
        this.id = id;
    }

    public List<Integer> getQueue() {
        return queue;
    }

    public int getId() {
        return id;
    }

    public void assignCustomerToQueue(int customerId) {
        queue.add(customerId);
    }
}
