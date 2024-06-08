package Producer;

import java.util.Random;

/**
 * Created by Stanislaw on 08.05.2018.
 */
public class Producer {
    int timeToNext;
    private Random random;

    public Producer() {
        random = new Random();
        timeToNext = generateTimeToNext();
    }

    public int produce()
    {
        timeToNext=generateTimeToNext();
        int count = random.nextInt(4)+1;
        System.out.println("I produced " + count + ". Next I'll produce in " + timeToNext);
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
