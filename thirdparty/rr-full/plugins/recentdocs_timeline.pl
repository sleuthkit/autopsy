#-----------------------------------------------------------
# recentdocs_timeline.pl
# Plugin for Registry Ripper 
# Parses RecentDocs keys/values in NTUSER.DAT into a timeline based on the MRUListEx 
# The times are printed in UTC in Unix epoch format, line 98/99 are interchangeable to modify the output format of the date.
#
# This script is a modified version of Harlen Carvey's recentdocs plugin. 
# This is an automated version of the process shown by Dan Pullega
# References: http://www.4n6k.com/2014/02/forensics-quickie-pinpointing-recent.html
# Note that these times should be used in conjunction with other artefacts as during testing I saw that not every item I accessed was stored in the ntuser.dat
# Also downloaded files appeared to be accessed, even though they werent.
# More testing is required


# Change history
#	 20161112 - fixed name
#	 20161116 - fixed presentation of data and added code to deal with entries with no values
#	 20161115 - rename plugin and updated output to include human-readable date
#	 20140224 - Fixed bug that took the lowest MRUList item, rather than the first
#	 20140222 - Modified to combine last write times into MRUListEx
#    20100405 - Updated to use Encode::decode to translate strings
#    20090115 - Minor update to keep plugin from printing terminating
#               MRUListEx value of 0xFFFFFFFF
#    20080418 - Minor update to address NTUSER.DAT files that have
#               MRUList values in this key, rather than MRUListEx
#               values
#
# References
#
# 
# Original copyright 2010 Quantum Analytics Research, LLC
# Updated by Phill Moore - github.com/randomaccess3
#-----------------------------------------------------------
package recentdocs_timeline;
use strict;
use Encode;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20161112);

sub getShortDescr {
	return "Gets contents of user's RecentDocs key and place last write times into timeline based on MRUListEx";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching recentdocs_timeline v.".$VERSION);
	::rptMsg("recentdocs_timeline v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my %hash = {};

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\RecentDocs";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("RecentDocs");
		#::rptMsg("**All values printed in MRUList\\MRUListEx order.");
		#::rptMsg($key_path);
		#::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");


# Get RecentDocs subkeys' values		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				#::rptMsg($key_path."\\".$s->get_name());
				#::rptMsg("LastWrite Time ".gmtime($s->get_timestamp())." (UTC)");
				
				my %rdvals = getRDValues($s);
				if (%rdvals) {
					my $tag;
					if (exists $rdvals{"MRUListEx"}) {
						$tag = "MRUListEx";
					}
					elsif (exists $rdvals{"MRUList"}) {
						$tag = "MRUList";
					}
					else {
				
					}
					
					my @list = split(/,/,$rdvals{$tag});
					my ($lastAccessed,@rest) = split(',', $rdvals{$tag});
					
					my $d = $s->get_timestamp();    #unix time
					#my $d = gmtime($s->get_timestamp()); #normalised time
					
					my $v = $rdvals{$lastAccessed};
					$hash{ $v } = $d;
					
					#This section was added after noticing that sometimes keys have no values in them. They still have names and dates/times
					if ($v eq ""){
						::rptMsg(gmtime($hash{$v})."\t\t:\tNO VALUES - CHECK KEY MANUALLY");
					}
					else{
						::rptMsg(gmtime($hash{$v})."\t\t:\t".$v);
					}
					
					#::rptMsg($tag." = ".$rdvals{$tag});
					#foreach my $i (@list) {
					#	::rptMsg("  ".$i." = ".$rdvals{$i});
					#}
				}
				else {
					::rptMsg($key_path." has no values.");
				}
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}

::rptMsg("");
::rptMsg("The last write times are now placed in line with the values in the MRUListEx value ");










# Get RecentDocs values		
		my %rdvals = getRDValues($key);
		if (%rdvals) {
			my $tag;
			if (exists $rdvals{"MRUListEx"}) {
				$tag = "MRUListEx";
			}
			elsif (exists $rdvals{"MRUList"}) {
				$tag = "MRUList";
			}
			else {
				
			}
			
			my @list = split(/,/,$rdvals{$tag});
			foreach my $i (@list) {
				if($hash{$rdvals{$i}}){
					::rptMsg("\t".gmtime($hash{$rdvals{$i}})."\t\t".$i." = ".$rdvals{$i});
				}
				else{
					::rptMsg("\t\t\t\t\t".$i." = ".$rdvals{$i})
				}
			}
			::rptMsg("");
		}
		else {
			::rptMsg($key_path." has no values.");
			::logMsg("Error: ".$key_path." has no values.");
		}

	}
	else {
		::rptMsg($key_path." not found.");
	}
}


sub getRDValues {
	my $key = shift;
	
	my $mru = "MRUList";
	my %rdvals;
	
	my @vals = $key->get_list_of_values();
	if (scalar @vals > 0) {
		foreach my $v (@vals) {
			my $name = $v->get_name();
			my $data = $v->get_data();
			if ($name =~ m/^$mru/) {
				my @mru;
				if ($name eq "MRUList") {
					@mru = split(//,$data);
				}
				elsif ($name eq "MRUListEx") {
					@mru = unpack("V*",$data);
				}
# Horrible, ugly cludge; the last, terminating value in MRUListEx
# is 0xFFFFFFFF, so we remove it.
				pop(@mru);
				$rdvals{$name} = join(',',@mru);
			}
			else {
# New code
				$data = decode("ucs-2le", $data);
				my $file = (split(/\00/,$data))[0];
#				my $file = (split(/\00\00/,$data))[0];
#				$file =~ s/\00//g;
				$rdvals{$name} = $file;
			}
		}
		return %rdvals;
	}
	else {
		return undef;
	}
}

1;
