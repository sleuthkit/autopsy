#-----------------------------------------------------------
# officedocs.pl
#   Plugin for Registry Ripper 
#
# Change history
#   20110830 [fpi] + banner, no change to the version number
#
# References
# 
# copyright 2008 H. Carvey
#-----------------------------------------------------------
package officedocs;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20080324);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's Office doc MRU keys";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching officedocs v.".$VERSION);
    ::rptMsg("officedocs v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner

	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	::rptMsg("officedocs v.".$VERSION);
# First, let's find out which version of Office is installed
	my $version;
	my $tag = 0;
	my @versions = ("7\.0","8\.0", "9\.0", "10\.0", "11\.0","12\.0");
	foreach my $ver (@versions) {
		my $key_path = "Software\\Microsoft\\Office\\".$ver."\\Common\\Open Find";
		if (defined($root_key->get_subkey($key_path))) {
			$version = $ver;
			$tag = 1;
		}
	}
	
	if ($tag) {
		::rptMsg("MSOffice version ".$version." located.");
		my $key_path = "Software\\Microsoft\\Office\\".$version;	                 
		my $of_key = $root_key->get_subkey($key_path);
		if ($of_key) {
# Attempt to retrieve Word docs			
			my @funcs = ("Open","Save As","File Save");
			foreach my $func (@funcs) {
				my $word = "Common\\Open Find\\Microsoft Office Word\\Settings\\".$func."\\File Name MRU";
				my $word_key = $of_key->get_subkey($word);
				if ($word_key) {
					::rptMsg($word);
					::rptMsg("LastWrite Time ".gmtime($word_key->get_timestamp())." (UTC)");
					::rptMsg("");
					my $value = $word_key->get_value("Value")->get_data();
					my @data = split(/\00/,$value);
					map{::rptMsg("$_");}@data;
				}
				else {
#					::rptMsg("Could not access ".$word);
				}
				::rptMsg("");
			}
# Attempt to retrieve Excel docs
			my $excel = 'Excel\\Recent Files';
			if (my $excel_key = $of_key->get_subkey($excel)) {
				::rptMsg($key_path."\\".$excel);
				::rptMsg("LastWrite Time ".gmtime($excel_key->get_timestamp())." (UTC)");
				my @vals = $excel_key->get_list_of_values();
				if (scalar(@vals) > 0) {
					my %files;
# Retrieve values and load into a hash for sorting			
					foreach my $v (@vals) {
						my $val = $v->get_name();
						my $data = $v->get_data();
						my $tag = (split(/File/,$val))[1];
						$files{$tag} = $val.":".$data;
					}
# Print sorted content to report file			
					foreach my $u (sort {$a <=> $b} keys %files) {
						my ($val,$data) = split(/:/,$files{$u},2);
						::rptMsg("  ".$val." -> ".$data);
					}
				}
				else {
					::rptMsg($key_path.$excel." has no values.");
				}
			}
			else {
				::rptMsg($key_path.$excel." not found.");
			}
			::rptMsg("");
# Attempt to retrieve PowerPoint docs			
			my $ppt = 'PowerPoint\\Recent File List';
			if (my $ppt_key = $of_key->get_subkey($ppt)) {
				::rptMsg($key_path."\\".$ppt);
				::rptMsg("LastWrite Time ".gmtime($ppt_key->get_timestamp())." (UTC)");
				my @vals = $ppt_key->get_list_of_values();
				if (scalar(@vals) > 0) {
					my %files;
# Retrieve values and load into a hash for sorting			
					foreach my $v (@vals) {
						my $val = $v->get_name();
						my $data = $v->get_data();
						my $tag = (split(/File/,$val))[1];
						$files{$tag} = $val.":".$data;
					}
# Print sorted content to report file			
					foreach my $u (sort {$a <=> $b} keys %files) {
						my ($val,$data) = split(/:/,$files{$u},2);
						::rptMsg("  ".$val." -> ".$data);
					}
				}
				else {
					::rptMsg($key_path."\\".$ppt." has no values.");
				}		
			}
			else {
				::rptMsg($key_path."\\".$ppt." not found.");
			}			
		}
		else {
			::rptMsg("Could not access ".$key_path);
			::logMsg("Could not access ".$key_path);
		}
	}
	else {
		::logMsg("MSOffice version not found.");
		::rptMsg("MSOffice version not found.");
	}
}

1;