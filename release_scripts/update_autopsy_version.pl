#!/usr/bin/perl

# Updates various Autopsy version numbers 

use strict;
use File::Copy;

# global variables
my $VER;


my $TESTING = 0;
print "TESTING MODE (no commits)\n" if ($TESTING);



sub main {

	# Get the Autopsy version argument
	if (scalar (@ARGV) != 1) {
	    print stderr "Missing release version argument (i.e.  4.9.0)\n";
	    exit;
	}
	
	$VER = $ARGV[0];
	die "Invalid version number: $VER (1.2.3 or 1.2.3b1 expected)" unless ($VER =~ /^\d+\.\d+\.\d+(b\d+)?$/);
	
	
	my $AUT_RELNAME = "autopsy-${VER}";
	# Verify the tag doesn't already exist
	exec_pipe(*OUT, "git tag | grep \"${AUT_RELNAME}\$\"");
	my $foo = read_pipe_line(*OUT);
	if ($foo ne "") {
		print "Tag ${AUT_RELNAME} already exists\n";
		print "Remove with 'git tag -d ${AUT_RELNAME}'\n";
		die "stopping";
	}
	close(OUT);
	
	# Assume we running out of 'release' folder
	chdir ".." or die "Error changing directories to root";
	
	
	# verify_precheckin();
	
	
	# Update the version info in that tag
	update_project_properties();
	update_doxygen_dev();
	update_doxygen_user();
	
	print "Files updated.  You need to commit and push them\n";
}





######################################################
# Utility functions


# Function to execute a command and send output to pipe
# returns handle
# exec_pipe(HANDLE, CMD);
sub exec_pipe {
    my $handle = shift(@_);
    my $cmd    = shift(@_);

    die "Can't open pipe for exec_pipe"
      unless defined(my $pid = open($handle, '-|'));

    if ($pid) {
        return $handle;
    }
    else {
        $| = 1;
        exec("$cmd") or die "Can't exec program: $!";
    }
}

# Read a line of text from an open exec_pipe handle
sub read_pipe_line {
    my $handle = shift(@_);
    my $out;

    for (my $i = 0; $i < 100; $i++) {
        $out = <$handle>;
        return $out if (defined $out);
    }
    return $out;
}


# Prompt user for argument and return response
sub prompt_user {
    my $q = shift(@_);
    print "$q: ";
    $| = 1;
    $_ = <STDIN>;
    chomp;
    return $_;
}



#############################################
# File update methods



# Verify that all files in the current source directory
# are checked in.  dies if any are modified.
sub verify_precheckin {

    #system ("git pull");

    print "Verifying everything is checked in\n";
    exec_pipe(*OUT, "git status -s | grep \"^ M\"");

    my $foo = read_pipe_line(*OUT);
    if ($foo ne "") {
        print "Files not checked in\n";
        while ($foo ne "") {
            print "$foo";
            $foo = read_pipe_line(*OUT);
        }
        die "stopping" unless ($TESTING);
    }
    close(OUT);

    print "Verifying everything is pushed\n";
    exec_pipe(*OUT, "git status -sb | grep \"^##\" | grep \"ahead \"");
    my $foo = read_pipe_line(*OUT);
    if ($foo ne "") {
            print "$foo";
        print "Files not pushed to remote\n";
        die "stopping" unless ($TESTING);
    }
    close(OUT);
}



# update the version in nbproject/project.properties 
sub update_project_properties {

    my $orig = "project.properties";
    my $temp = "${orig}-bak";

    print "Updating the version in ${orig}\n";
    
    chdir "nbproject" or die "cannot change into nbproject directory";
    

    open (CONF_IN, "<${orig}") or die "Cannot open ${orig}";
    open (CONF_OUT, ">${temp}") or die "Cannot open ${temp}";

    my $found = 0;
    while (<CONF_IN>) {
        if (/^app\.version=/) {
            print CONF_OUT "app.version=$VER\n";
            $found++;
        }
        else {
            print CONF_OUT $_;
        }
    }
    close (CONF_IN);
    close (CONF_OUT);

    if ($found != 1) {
        die "$found (instead of 1) occurrences of app.version found in ${orig}";
    }

    unlink ($orig) or die "Error deleting ${orig}";
    rename ($temp, $orig) or die "Error renaming tmp $orig file";
    system("git add ${orig}") unless ($TESTING);
    chdir ".." or die "Error changing directories back to root";
}



# update the dev docs
sub update_doxygen_dev {

    my $orig = "Doxyfile";
    my $temp = "${orig}-bak";

    print "Updating the version in ${orig} (Dev)\n";
    
    chdir "docs/doxygen" or die "cannot change into docs/doxygen directory";
    

    open (CONF_IN, "<${orig}") or die "Cannot open ${orig}";
    open (CONF_OUT, ">${temp}") or die "Cannot open ${temp}";

    my $found = 0;
    while (<CONF_IN>) {
        if (/^PROJECT_NUMBER/) {
            print CONF_OUT "PROJECT_NUMBER = ${VER}\n";
            $found++;
        }
        elsif (/^HTML_OUTPUT/) {
            print CONF_OUT "HTML_OUTPUT = api-docs/${VER}/\n";
            $found++;
        }     
        else {
            print CONF_OUT $_;
        }
    }
    close (CONF_IN);
    close (CONF_OUT);

    if ($found != 2) {
        die "$found (instead of 2) occurrences of version found in (DEV) ${orig}";
    }

    unlink ($orig) or die "Error deleting ${orig}";
    rename ($temp, $orig) or die "Error renaming tmp $orig file";
    system("git add ${orig}") unless ($TESTING);
    chdir "../.." or die "Error changing directories back to root";
}


# update the user docs 
sub update_doxygen_user {

    my $orig = "Doxyfile";
    my $temp = "${orig}-bak";

    print "Updating the version in ${orig} (User)\n";
    
    chdir "docs/doxygen-user" or die "cannot change into docs/doxygen-user directory";
    

    open (CONF_IN, "<${orig}") or die "Cannot open ${orig}";
    open (CONF_OUT, ">${temp}") or die "Cannot open ${temp}";

    my $found = 0;
    while (<CONF_IN>) {
        if (/^PROJECT_NUMBER/) {
            print CONF_OUT "PROJECT_NUMBER = ${VER}\n";
            $found++;
        }
        elsif (/^HTML_OUTPUT/) {
            print CONF_OUT "HTML_OUTPUT = ${VER}\n";
            $found++;
        }     
        else {
            print CONF_OUT $_;
        }
    }
    close (CONF_IN);
    close (CONF_OUT);

    if ($found != 2) {
        die "$found (instead of 2) occurrences of version found in (USER) ${orig}";
    }

    unlink ($orig) or die "Error deleting ${orig}";
    rename ($temp, $orig) or die "Error renaming tmp $orig file";
    system("git add ${orig}") unless ($TESTING);
    chdir "../.." or die "Error changing directories back to root";
}


main();