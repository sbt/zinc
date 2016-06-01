package xsbti.compile;

public interface Compilers
{
	JavaCompiler javac();
	// should be cached by client if desired
	ScalaCompiler scalac();
}
