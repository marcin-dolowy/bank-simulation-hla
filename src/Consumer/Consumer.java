package Consumer;

import java.util.Random;

/**
 * Created by Stanislaw on 08.05.2018.
 */
public class Consumer {
    int timeToNext;
    private Random random;

    public Consumer() {
        random = new Random();
        timeToNext = generateTimeToNext();
    }

    public int consume()
    {
        timeToNext=generateTimeToNext();
        int count = random.nextInt(4)+1;
        System.out.println("I want to consume " + count + ". Next I'll be consuming in " + timeToNext);
        return count;
    }

    public int getTimeToNext() {
        return timeToNext;
    }

    private int generateTimeToNext()
    {
        return random.nextInt(10)+1;
    }
}
