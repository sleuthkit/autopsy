#!/usr/bin/perl

# Updates various TSK version numbers 
# use this when the version of TSK that Autopsy depends on changes

use strict;
use File::Copy;

# global variables
my $VER;

my $TESTING = 0;
print "TESTING MODE (no commits)\n" if ($TESTING);


sub main {

	# Get the TSK version argument
	if (scalar (@ARGV) != 1) {
	    print stderr "Missing release version argument (i.e.  4.9.0)\n";
	    exit;
	}
	
	$VER = $ARGV[0];
	die "Invalid version number: $VER (1.2.3 or 1.2.3b1 expected)" unless ($VER =~ /^\d+\.\d+\.\d+(b\d+)?$/);
	
	# Assume we running out of 'release' folder
	chdir ".." or die "Error changing directories to root";
	
	# Update the version info in that tag
	update_tsk_version();
	update_core_project_properties();
	update_core_project_xml();
    update_unix_setup();
    
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



#############################################
# File update methods



# update the tskversion.xml
sub update_tsk_version {

    my $orig = "TSKVersion.xml";
    my $temp = "${orig}-bak";

    print "Updating the version in ${orig}\n";
    
    open (CONF_IN, "<${orig}") or die "Cannot open ${orig}";
    open (CONF_OUT, ">${temp}") or die "Cannot open ${temp}";

    my $found = 0;
    while (<CONF_IN>) {
        if (/name="TSK_VERSION" value=/) {
            print CONF_OUT "    <property name=\"TSK_VERSION\" value=\"${VER}\"/>\n";
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
    
}



sub update_core_project_properties {

    my $orig = "project.properties";
    my $temp = "${orig}-bak";

    print "Updating the version in ${orig}\n";
    
    chdir "Core/nbproject" or die "cannot change into Core/nbproject directory";
    

    open (CONF_IN, "<${orig}") or die "Cannot open ${orig}";
    open (CONF_OUT, ">${temp}") or die "Cannot open ${temp}";

    my $found = 0;
    while (<CONF_IN>) {
        if (/^file\.reference\.sleuthkit\-4/) {
            print CONF_OUT "file.reference.sleuthkit-${VER}.jar=release/modules/ext/sleuthkit-${VER}.jar\n";
            $found++;
        }
        elsif (/^file\.reference\.sleuthkit\-caseuco-4/) {
            print CONF_OUT "file.reference.sleuthkit-caseuco-${VER}.jar=release/modules/ext/sleuthkit-caseuco-${VER}.jar\n";
            $found++;
        }
            
        else {
            print CONF_OUT $_;
        }
    }
    close (CONF_IN);
    close (CONF_OUT);

    if ($found != 2) {
        die "$found (instead of 2) occurrences of version found in core ${orig}";
    }

    unlink ($orig) or die "Error deleting ${orig}";
    rename ($temp, $orig) or die "Error renaming tmp $orig file";
    system("git add ${orig}") unless ($TESTING);
    chdir "../.." or die "Error changing directories back to root";
}

sub update_core_project_xml {

    my $orig = "project.xml";
    my $temp = "${orig}-bak";

    print "Updating the version in ${orig}\n";
    
    chdir "Core/nbproject" or die "cannot change into Core/nbproject directory";
    
    open (CONF_IN, "<${orig}") or die "Cannot open ${orig}";
    open (CONF_OUT, ">${temp}") or die "Cannot open ${temp}";

    my $found = 0;
    while (<CONF_IN>) {
        if (/<runtime-relative-path>ext\/sleuthkit-4/) {
            print CONF_OUT "                <runtime-relative-path>ext/sleuthkit-${VER}.jar</runtime-relative-path>\n";
            $found++;
        }
        elsif (/<binary-origin>release\/modules\/ext\/sleuthkit-4/) {
            print CONF_OUT "                <binary-origin>release/modules/ext/sleuthkit-${VER}.jar</binary-origin>\n";
            $found++;
        }    
        elsif (/<runtime-relative-path>ext\/sleuthkit-caseuco-4/) {
            print CONF_OUT "                <runtime-relative-path>ext/sleuthkit-caseuco-${VER}.jar</runtime-relative-path>\n";
            $found++;
        }
        elsif (/<binary-origin>release\/modules\/ext\/sleuthkit-caseuco-4/) {
            print CONF_OUT "                <binary-origin>release/modules/ext/sleuthkit-caseuco-${VER}.jar</binary-origin>\n";
            $found++;
        }    
        else {
            print CONF_OUT $_;
        }
    }
    close (CONF_IN);
    close (CONF_OUT);

    if ($found != 4) {
        die "$found (instead of 4) occurrences of version found in case ${orig}";
    }

    unlink ($orig) or die "Error deleting ${orig}";
    rename ($temp, $orig) or die "Error renaming tmp $orig file";
    system("git add ${orig}") unless ($TESTING);
    chdir "../.." or die "Error changing directories back to root";
}


# update the tskversion.xml
sub update_unix_setup {
    
    my $orig = "unix_setup.sh";
    my $temp = "${orig}-bak";
    
    print "Updating the version in ${orig}\n";
    
    open (CONF_IN, "<${orig}") or die "Cannot open ${orig}";
    open (CONF_OUT, ">${temp}") or die "Cannot open ${temp}";
    
    my $found = 0;
    while (<CONF_IN>) {
        if (/^TSK_VERSION=/) {
            print CONF_OUT "TSK_VERSION=${VER}\n";
            $found++;
        }
        else {
            print CONF_OUT $_;
        }
    }
    close (CONF_IN);
    close (CONF_OUT);
    
    if ($found != 1) {
        die "$found (instead of 1) occurrences of TSK_VERSION found in ${orig}";
    }
    
    unlink ($orig) or die "Error deleting ${orig}";
    rename ($temp, $orig) or die "Error renaming tmp $orig file";
    system("git add ${orig}") unless ($TESTING);
    
}


main();
