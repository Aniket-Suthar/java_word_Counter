public class Main {
    public static void main(String[] args) {
        /**
         * This Function is used to generate the sample text files with 5 lacs of lines.
         */
//        LargeFileGenerator.generateTestFiles();

        /**
         * This is the main function which performs the word count in files
         */
        MultiFileWordCounter.processFiles("test_files");
    }
}
