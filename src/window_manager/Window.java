package window_manager;

import java.sql.SQLOutput;
import java.util.Random;

public class Window {
    private final int id;
    private boolean isAvailable = true;
    private double serviceTime;
    private final Random random = new Random();

    public Window(int id) {
        this.id = id;
        serviceTime = generateServiceTime();
    }

    public void startService() {
        isAvailable = false;
        System.out.printf("Window [%d]: customer is sered", id);
    }

    public void endService(){
        isAvailable = true;
        System.out.printf("Window [%d]: customer has finished being served", id);
    }


    private int generateServiceTime() {
        return random.nextInt(10) + 1;
    }

    public int getId() {
        return id;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public double getServiceTime() {
        return serviceTime;
    }
}