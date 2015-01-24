#-----------------------------------------------------------
# recentdocs_tln.pl
# 
#
# Change history
#    20140220 - updated
#
# References
#
# 
# copyright 2014 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package recentdocs_tln;
use strict;
use Encode;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20140220);

sub getShortDescr {
	return "Gets contents of user's RecentDocs key (TLN)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	
	my ($name, $lw, @list);
	
	::logMsg("Launching recentdocs_tln v.".$VERSION);
#	::rptMsg("recentdocs_tln v.".$VERSION); # banner
#  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\RecentDocs";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("RecentDocs");
#		::rptMsg("**All values printed in MRUList\\MRUListEx order.");
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		$lw = $key->get_timestamp();
# Get RecentDocs values		
		my %rdvals = getRDValues($key);
		if (%rdvals) {
			@list = split(/,/,$rdvals{"MRUListEx"});
			
			if (exists $rdvals{$list[0]}) {
				::rptMsg($lw."|REG|||RecentDocs - ".$key_path." - ".$rdvals{$list[0]});
			}
#			foreach my $i (@list) {
#				::rptMsg("  ".$i." = ".$rdvals{$i});
#			}
#			::rptMsg("");
		}
		else {
#			::rptMsg($key_path." has no values.");
		}
# Get RecentDocs subkeys' values		
	my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				$name = $key_path."\\".$s->get_name();
				$lw   = $s->get_timestamp();
				
				my %rdvals = getRDValues($s);
				if (%rdvals) {
				
					@list = split(/,/,$rdvals{"MRUListEx"});
					if (exists $rdvals{$list[0]}) {
						::rptMsg($lw."|REG|||RecentDocs - ".$name." - ".$rdvals{$list[0]});
					}
				}
				else {
#					::rptMsg($key_path." has no values.");
				}
			}
		}
		else {
#			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	}
}


sub getRDValues {
	my $key = shift;
	my %rdvals;
	my @mru = ();
	my @vals = $key->get_list_of_values();
	if (scalar @vals > 0) {
		foreach my $v (@vals) {
			my $name = $v->get_name();
			my $data = $v->get_data();
			if ($name eq "MRUListEx") {
				@mru = unpack("V*",$data);
			
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