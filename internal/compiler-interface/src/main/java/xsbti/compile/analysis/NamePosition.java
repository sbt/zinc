package xsbti.compile.analysis;

public interface NamePosition {

    /**
     * @return  Line position in a document whose <code>name</code> is defined (one-based).
     */
    public int line();

    /**
     * @return column offset on a <code>line</code> in a document whose <code>name</code> is defined (one-based).
     */
    public int column();

    /**
     * @return The source name
     */
    public String name();

    /**
     * @return The full path name corresponding to <code>name</code> in position
     *         that indicated by <code>line</code> and <code>column</code>.
     */
    public String fullName();
}
