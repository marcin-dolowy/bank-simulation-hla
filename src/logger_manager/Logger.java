package logger_manager;

public class Logger {

    public int getTimeToNext() {
        return timeToNext;
    }

    private int generateTimeToNext() {
        return random.nextInt(10) + 1;
    }
}
