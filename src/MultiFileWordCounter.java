import java.io.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class MultiFileWordCounter {
    private static final Logger logger = Logger.getLogger(MultiFileWordCounter.class.getName());
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();
    private static final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    private static final CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
    private static final String OUTPUT_FILE = "word_count_output.txt";
    private static final String LOG_FILE = "application.log";

    /**
     * This static block is used to initialized the logger for the file
     */
    static {
        try {
            FileHandler fileHandler = new FileHandler(LOG_FILE, true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.ALL);
        } catch (IOException e) {
            System.err.println("Failed to initialize log file: " + e.getMessage());
        }
    }

    /**
     * This is the main methods where the submission of tasks is done to threads and after completion of
     * tasks their results are also being taken.
     *
     * @param directoryPath - the folder path from where the testing files are needed to be taken.
     */
    public static void processFiles(String directoryPath) {
        logger.log(Level.INFO, "Current thread count: {0}", THREAD_COUNT);
        File folder = new File(directoryPath);
        if (!folder.exists() || !folder.isDirectory()) {
            logger.log(Level.SEVERE, "Invalid directory: {0}", directoryPath);
            return;
        }

        /**
         * Fetching all the files from the directory and storing it in the Array of files.
         */
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null || files.length == 0) {
            logger.warning("No text files found in directory.");
            return;
        }

        /**
         * Creating a buffered output stream to write the data of the word count to the output file
         */
        try (BufferedOutputStream writer = new BufferedOutputStream(new FileOutputStream(OUTPUT_FILE, true))) {
            logger.info("Starting file processing...");

            /**
             * Here the files names are fetched from the array of files and the those file names are submit to threads
             * for counting the words.
             */
            for (File file : files) {
                logger.log(Level.INFO, "Submitting file: {0}", file.getName());
                completionService.submit(new WordCountTask(file));
            }

            /**
             * In this we iterate over the array of files and then check whether the wordcount task is completed
             * then take the result from the corresponding thread.
             */
            for (int i = 0; i < files.length; i++) {
                try {
                    Future<String> future = completionService.take(); // Get completed task
                    String result = future.get();

                    /**
                     * The syncronized block is assigned in order to make writing available for one
                     * thread at a time to avoid inconsistency to results.
                     */
                    synchronized (writer) {
                        writer.write(result.getBytes());
                        writer.flush();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    logger.log(Level.SEVERE, "Error processing file: {0}", e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error writing to output file: {0}", e.getMessage());
        } finally {
            executor.shutdown();
        }
        logger.info("Processing completed. Output written to " + OUTPUT_FILE);
    }

    /**
     * This static method is the main method where actual word count logic is implemented.
     */
    private static class WordCountTask implements Callable<String> {
        private final File file;

        /**
         * This parametric constructor is created because each thread requires the new instance of
         * the new file
         * @param file - the corresponding file whose word count are needed to be done.
         */
        WordCountTask(File file) {
            this.file = file;
        }

        /**
         * This is the call method of the callable interface in order to execute the word count method
         *
         * @return - the word count in file
         */
        @Override
        public String call() {
            StringBuilder result = new StringBuilder("\nFile: " + file.getName() + "\n");
            result.append("--------------------------------------------\n");

            try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file));
                 BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {

                String line;
                int lineCount = 0;
                while ((line = bufferedReader.readLine()) != null) {
                    lineCount++;
                    int wordCount = line.trim().isEmpty() ? 0 : line.trim().split("\\s+").length;
                    result.append(String.format("Line %d: %d words\n", lineCount, wordCount));
                    logger.log(Level.INFO, "Processed {0}: Line {1} -> {2} words", new Object[]{file.getName(), lineCount, wordCount});
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error reading file {0}: {1}", new Object[]{file.getName(), e.getMessage()});
                return "File: " + file.getName() + " - Error reading file.\n";
            }
            logger.log(Level.INFO, "Successfully processed file: {0}", file.getName());
            return result.toString();
        }
    }
}
