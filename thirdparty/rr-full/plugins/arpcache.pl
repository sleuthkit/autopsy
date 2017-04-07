#-----------------------------------------------------------
# arpcache.pl
# Retrieves CurrentVersion\App Management\ARPCache entries; subkeys appear
# to maintain information about paths to installed applications in the 
# SlowInfoCache value(0x10 - FILETIME object, null term. string with path
# starts at 0x1c)
#
# Change history
#    20090413 - Created
#
# References
#    No references, but the subkeys appear to hold information about
#    installed applications; some SlowInfoCache values appear to contain
#    timestamp data (FILETIME object) and/or path information.  Posts on
#    the Internet indicate the existence of Kazaa beneath the APRCache key,
#    as well as possibly an "Outerinfo" subkey indicating that spyware is 
#    installed.
# 
# copyright 2009 H. Carvey
#-----------------------------------------------------------
package arpcache;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20090413);

sub getConfig{return %config}
sub getShortDescr {
	return "Retrieves CurrentVersion\\App Management\\ARPCache entries";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %arpcache;

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching arpcache v.".$VERSION);
	::rptMsg("arpcache v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\App Management\\ARPCache';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) { 
				my $lw = $s->get_timestamp();
				my $name = $s->get_name();
	
				my $path;
				eval {
					my $i = $s->get_value("SlowInfoCache")->get_data();
					$path = parsePath($i);
				};
				($@) ? ($name .= "|") : ($name .= "|".$path);
				
				my $date;
				eval {
					my $i = $s->get_value("SlowInfoCache")->get_data();
					$date = parseDate($i);
				};
				($@) ? ($name .= "|") : ($name .= "|".$date);
				push(@{$arpcache{$lw}},$name);
			}
			
			
			foreach my $t (reverse sort {$a <=> $b} keys %arpcache) {
				::rptMsg(gmtime($t)." (UTC)");
				foreach my $item (@{$arpcache{$t}}) {
					my ($name,$path,$date) = split(/\|/,$item,3);
					::rptMsg("  ".$name);
					my $str = $path unless ($path eq "");
					$str .= " [".gmtime($date)."]" unless ($date == 0);
					::rptMsg("    -> ".$str) unless ($str eq ""); 
				}
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
			::logMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}

1;

sub parseDate {
	my $data = shift;
	my ($t1,$t2) = unpack("VV",substr($data,0x10,8));
	return ::getTime($t1,$t2);
}

sub parsePath {
	my $data = shift;
	my $ofs = 0x1c;
	my $tag = 1;
	
	my $str = substr($data,$ofs,2);
	if (unpack("v",$str) == 0) {
		return "";
	}
	else {
		while($tag) {
			$ofs += 2;
			my $i = substr($data,$ofs,2);
			if (unpack("v",$i) == 0) {
				$tag = 0;
			}
			else {
				$str .= $i;
			}
		}
	}	
	$str =~ s/\00//g;
	return $str;
}