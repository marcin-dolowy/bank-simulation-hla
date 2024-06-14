package logger_manager;

import java.util.ArrayList;
import java.util.List;

public class Logger {
    private List<List<Integer>> queueLength;

    public Logger(){
        this.queueLength = new ArrayList<>();
        this.queueLength.add(new ArrayList<>());
        this.queueLength.add(new ArrayList<>());
    }

    public List<List<Integer>> getQueueLength() {
        return queueLength;
    }
}
