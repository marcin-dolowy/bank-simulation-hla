package customer_producer_manager;

import java.util.Random;

public class Producer {
    int lastCustomerId;
    int timeToNext;
    private final Random random;

    public Producer() {
        this.lastCustomerId = 0;
        this.random = new Random();
        this.timeToNext = generateTimeToNext();
    }

    public int produce() {
        timeToNext = generateTimeToNext();
        lastCustomerId++;
        System.out.println("I produced customer of ID:" + lastCustomerId + ". Next I'll produce in " + timeToNext);
        return lastCustomerId;
    }

    public int getTimeToNext() {
        return timeToNext;
    }

    private int generateTimeToNext() {
        return random.nextInt(10) + 1;
    }
}
