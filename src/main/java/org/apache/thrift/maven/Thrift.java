package org.apache.thrift.maven;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import com.google.common.collect.ImmutableSet;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.newHashSet;

/**
 * This class represents an invokable configuration of the {@code thrift}
 * compiler. The actual executable is invoked using the plexus
 * {@link Commandline}.
 * <p/>
 * This class currently only supports generating java source files.
 *
 * @author gak@google.com (Gregory Kick)
 */
final class Thrift
{
	

	public static final int THRIFT_IO_EXCEPTION = 1111;

	final static String GENERATED_JAVA = "gen-java";
	
	private final String executable;
	private final String generator;
	private final ImmutableSet<File> thriftPathElements;
	private final ImmutableSet<File> thriftFiles;
	private final File javaOutputDirectory;
	private final CommandLineUtils.StringStreamConsumer output;
	private final CommandLineUtils.StringStreamConsumer error;
	
	/**
	 * Constructs a new instance. This should only be used by the
	 * {@link Builder}.
	 *
	 * @param executable
	 *            The path to the {@code thrift} executable.
	 * @param generator
	 *            The value for the {@code --gen} option.
	 * @param thriftPath
	 *            The directories in which to search for imports.
	 * @param thriftFiles
	 *            The thrift source files to compile.
	 * @param javaOutputDirectory
	 *            The directory into which the java source files will be
	 *            generated.
	 */
	private Thrift(String executable, String generator, ImmutableSet<File> thriftPath, ImmutableSet<File> thriftFiles,
			File javaOutputDirectory)
	{
		this.executable = checkNotNull(executable, "executable");
		this.generator = checkNotNull(generator, "generator");
		this.thriftPathElements = checkNotNull(thriftPath, "thriftPath");
		this.thriftFiles = checkNotNull(thriftFiles, "thriftFiles");
		this.javaOutputDirectory = checkNotNull(javaOutputDirectory, "javaOutputDirectory");
		this.error = new CommandLineUtils.StringStreamConsumer();
		this.output = new CommandLineUtils.StringStreamConsumer();
	}
	
	/**
     * Invokes the {@code thrift} compiler using the configuration specified at
     * construction.
     *
     * @return The exit status of {@code thrift}.
     * @throws CommandLineException
	 * @throws IOException 
     */
    public int compile() throws CommandLineException{
    	
    	for (File thriftFile : thriftFiles) {
    		List<String> paramList = buildThriftCommand(thriftFile);
    		CommandLine cmdLine = CommandLine.parse(executable).addArguments(paramList.toArray(new String[]{}));
    		
    		DefaultExecutor exec = new DefaultExecutor();
    		
			// set up output stream to capture
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
			exec.setStreamHandler(streamHandler);
    		exec.setExitValue(0);
			try
			{
				Map<String, String> env = EnvironmentUtils.getProcEnvironment();
				// make sure *nix systems can find their libs
				if(System.getProperty("os.name").toLowerCase().indexOf("nux") >= 0)
				{
					env.put("LD_LIBRARY_PATH", "/usr/local/lib/");
				}
				int result = exec.execute(cmdLine, env);
				if(result != 0)
	    		{
					output.consumeLine(String.format("Thrift execution error: %d, Reason: %s", result, outputStream.toString()));
	    			return result;
	    		}
				else
				{
					output.consumeLine(String.format("Thrift file '%s' processed successfuly", thriftFile.getName()));
				}
			}
			catch (IOException e)
			{
				error.consumeLine(String.format("IOException: %s", e.getMessage()));
				error.consumeLine(outputStream.toString());
				e.printStackTrace();
				return THRIFT_IO_EXCEPTION;
			}
    		
    	}
        return 0;
    }
	
	/**
	 * Creates the command line arguments.
	 * <p/>
	 * This method has been made visible for testing only.
	 *
	 * @param thriftFile
	 * @return A list consisting of the executable followed by any arguments.
	 */
	List<String> buildThriftCommand(final File thriftFile)
	{
		final List<String> command = new ArrayList<String>();
		// add the executable
		for (File thriftPathElement : thriftPathElements)
		{
			command.add("-I");
			command.add(thriftPathElement.toString());
		}
		command.add("-out");
		command.add(javaOutputDirectory.toString());
		command.add("--gen");
		command.add(generator);
		command.add(thriftFile.toString());
		return command;
	}
	
	/**
	 * @return the output
	 */
	public String getOutput()
	{
		return output.getOutput();
	}
	
	/**
	 * @return the error
	 */
	public String getError()
	{
		return error.getOutput();
	}
	
	/**
	 * This class builds {@link Thrift} instances.
	 *
	 * @author gak@google.com (Gregory Kick)
	 */
	static final class Builder
	{
		private final String executable;
		private final File javaOutputDirectory;
		private Set<File> thriftPathElements;
		private Set<File> thriftFiles;
		private String generator;
		
		/**
		 * Constructs a new builder. The two parameters are present as they are
		 * required for all {@link Thrift} instances.
		 *
		 * @param executable
		 *            The path to the {@code thrift} executable.
		 * @param javaOutputDirectory
		 *            The directory into which the java source files will be
		 *            generated.
		 * @throws NullPointerException
		 *             If either of the arguments are {@code null}.
		 * @throws IllegalArgumentException
		 *             If the {@code javaOutputDirectory} is not a directory.
		 */
		public Builder(String executable, File javaOutputDirectory)
		{
			this.executable = checkNotNull(executable, "executable");
			this.javaOutputDirectory = checkNotNull(javaOutputDirectory);
			checkArgument(javaOutputDirectory.isDirectory());
			this.thriftFiles = newHashSet();
			this.thriftPathElements = newHashSet();
		}
		
		/**
		 * Adds a thrift file to be compiled. Thrift files must be on the
		 * thriftpath and this method will fail if a thrift file is added
		 * without first adding a parent directory to the thriftpath.
		 *
		 * @param thriftFile
		 * @return The builder.
		 * @throws IllegalStateException
		 *             If a thrift file is added without first adding a parent
		 *             directory to the thriftpath.
		 * @throws NullPointerException
		 *             If {@code thriftFile} is {@code null}.
		 */
		public Builder addThriftFile(File thriftFile)
		{
			checkNotNull(thriftFile);
			checkArgument(thriftFile.isFile());
			checkArgument(thriftFile.getName().endsWith(".thrift"));
			checkThriftFileIsInThriftPath(thriftFile);
			thriftFiles.add(thriftFile);
			return this;
		}
		
		/**
		 * Adds the option string for the Thrift executable's {@code --gen}
		 * parameter.
		 *
		 * @param generator
		 * @return The builder
		 * @throws NullPointerException
		 *             If {@code generator} is {@code null}.
		 */
		public Builder setGenerator(String generator)
		{
			checkNotNull(generator);
			this.generator = generator;
			return this;
		}
		
		private void checkThriftFileIsInThriftPath(File thriftFile)
		{
			assert thriftFile.isFile();
			checkState(checkThriftFileIsInThriftPathHelper(thriftFile.getParentFile()));
		}
		
		private boolean checkThriftFileIsInThriftPathHelper(File directory)
		{
			assert directory.isDirectory();
			if (thriftPathElements.contains(directory))
			{
				return true;
			}
			else
			{
				final File parentDirectory = directory.getParentFile();
				return (parentDirectory == null) ? false : checkThriftFileIsInThriftPathHelper(parentDirectory);
			}
		}
		
		/**
		 * @see #addThriftFile(File)
		 */
		public Builder addThriftFiles(Iterable<File> thriftFiles)
		{
			for (File thriftFile : thriftFiles)
			{
				addThriftFile(thriftFile);
			}
			return this;
		}
		
		/**
		 * Adds the {@code thriftPathElement} to the thriftPath.
		 *
		 * @param thriftPathElement
		 *            A directory to be searched for imported thrift message
		 *            buffer definitions.
		 * @return The builder.
		 * @throws NullPointerException
		 *             If {@code thriftPathElement} is {@code null}.
		 * @throws IllegalArgumentException
		 *             If {@code thriftPathElement} is not a directory.
		 */
		public Builder addThriftPathElement(File thriftPathElement)
		{
			checkNotNull(thriftPathElement);
			checkArgument(thriftPathElement.isDirectory());
			thriftPathElements.add(thriftPathElement);
			return this;
		}
		
		/**
		 * @see #addThriftPathElement(File)
		 */
		public Builder addThriftPathElements(Iterable<File> thriftPathElements)
		{
			for (File thriftPathElement : thriftPathElements)
			{
				addThriftPathElement(thriftPathElement);
			}
			return this;
		}
		
		/**
		 * @return A configured {@link Thrift} instance.
		 * @throws IllegalStateException
		 *             If no thrift files have been added.
		 */
		public Thrift build()
		{
			checkState(!thriftFiles.isEmpty());
			return new Thrift(executable, generator, ImmutableSet.copyOf(thriftPathElements),
					ImmutableSet.copyOf(thriftFiles), javaOutputDirectory);
		}
	}
}
