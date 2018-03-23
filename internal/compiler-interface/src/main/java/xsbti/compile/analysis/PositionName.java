package xsbti.compile.analysis;

public interface PositionName {
    public int line();

    public int column();

    public String name();

    public String fullName();
}
