#-----------------------------------------------------------
# officedocs2010.pl
#   Plugin to parse Office 2010 MRU entries (Word, Excel, Access, and PowerPoint)
#
# Change history
#   20010415 [fpi] * added this banner and change the name from "officedocs"
#                    to "officedocs2010", since this plugins is little different
#                    from Harlan's one (merging suggested)
#   20110830 [fpi] + banner, no change to the version number
#   20110902 [hca] - removed the use of "DateTime::Format::WindowsFileTime"
#                    module, the Windows 64bit FILETIME is converted using
#                    internal RegRipper facility
#
# References
#   http://accessdata.com/downloads/media/
#       Microsoft_Office_2007-2010_Registry_ArtifactsFINAL.pdf
# 
# copyright 2011 Cameron Howell
#-----------------------------------------------------------

package officedocs2010;
use strict;
# use DateTime::Format::WindowsFileTime; # 20110902 [hca] - removed WindowsFileTime module

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20110902);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's Office doc MRU keys";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("Access Data Office 2007 2010 Registry Artifacts" => 
	            "http://accessdata.com/downloads/media/Microsoft_Office_2007-2010_Registry_ArtifactsFINAL.pdf");
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getWinTS {
	my $data = $_[0];
	my $winTS;
    my $dateTime; # 20110902 [hca] - removed WindowsFileTime module
	(my $prefix, my $suffix) = split(/\*/,$data);
	if ($prefix =~ /\[.{9}\]\[T(.{16})\]/) {
		$winTS = $1;
# 20110902 [hca] - removed WindowsFileTime module -- BEGIN
        my @vals = split(//,$winTS);
        my $t0 = join('',@vals[0..7]);
        my $t1 = join('',@vals[8..15]);
        $dateTime = ::getTime(hex($t1),hex($t0));
        # WAS: 
        # }
        # my $dateTime = DateTime::Format::WindowsFileTime->parse_datetime($winTS);
        # $dateTime =~ s/T/ /;
        # my $formattedTxt = ($suffix . "\t" . $dateTime);
        # return $formattedTxt;
    }
    return ($suffix ."  ". gmtime($dateTime));
# 20110902 [hca] - removed WindowsFileTime module -- END
}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching officedocs2010 v.".$VERSION);
    ::rptMsg("officedocs2010 v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner

	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	# ::rptMsg("officedocs v.".$VERSION); # 20110830 [fpi] - redundant
	my $tag = 0;
	my $key_path = "Software\\Microsoft\\Office\\14.0";
	if (defined($root_key->get_subkey($key_path))) {
		$tag = 1;
	}
	
	if ($tag) {
		::rptMsg("MSOffice version 2010 located.");
		my $key_path = "Software\\Microsoft\\Office\\14.0";	                 
		my $of_key = $root_key->get_subkey($key_path);
		if ($of_key) {
# Attempt to retrieve Word docs
			my $word = 'Word\\File MRU';
			if (my $word_key = $of_key->get_subkey($word)) {
				::rptMsg($key_path."\\".$word);
				::rptMsg("LastWrite Time ".gmtime($word_key->get_timestamp())." (UTC)");
				my @vals = $word_key->get_list_of_values();
				if (scalar(@vals) > 0) {
					my %files;
# Retrieve values and load into a hash for sorting			
					foreach my $v (@vals) {
						my $val = $v->get_name();
						if ($val eq "Max Display") { next; }
						my $data = getWinTS($v->get_data());
						my $tag = (split(/Item/,$val))[1];
						$files{$tag} = $val.":".$data;
					}
# Print sorted content to report file			
					foreach my $u (sort {$a <=> $b} keys %files) {
						my ($val,$data) = split(/:/,$files{$u},2);
						::rptMsg("  ".$val." -> ".$data);
					}
				}
				else {
					::rptMsg($key_path.$word." has no values.");
				}
			}
			else {
				::rptMsg($key_path.$word." not found.");
			}
			::rptMsg("");
# Attempt to retrieve Excel docs
			my $excel = 'Excel\\File MRU';
			if (my $excel_key = $of_key->get_subkey($excel)) {
				::rptMsg($key_path."\\".$excel);
				::rptMsg("LastWrite Time ".gmtime($excel_key->get_timestamp())." (UTC)");
				my @vals = $excel_key->get_list_of_values();
				if (scalar(@vals) > 0) {
					my %files;
# Retrieve values and load into a hash for sorting			
					foreach my $v (@vals) {
						my $val = $v->get_name();
						if ($val eq "Max Display") { next; }
						my $data = getWinTS($v->get_data());
						my $tag = (split(/Item/,$val))[1];
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
# Attempt to retrieve Access docs
			my $access = 'Access\\File MRU';
			if (my $access_key = $of_key->get_subkey($access)) {
				::rptMsg($key_path."\\".$access);
				::rptMsg("LastWrite Time ".gmtime($access_key->get_timestamp())." (UTC)");
				my @vals = $access_key->get_list_of_values();
				if (scalar(@vals) > 0) {
					my %files;
# Retrieve values and load into a hash for sorting			
					foreach my $v (@vals) {
						my $val = $v->get_name();
						if ($val eq "Max Display") { next; }
						my $data = getWinTS($v->get_data());
						my $tag = (split(/Item/,$val))[1];
						$files{$tag} = $val.":".$data;
					}
# Print sorted content to report file			
					foreach my $u (sort {$a <=> $b} keys %files) {
						my ($val,$data) = split(/:/,$files{$u},2);
						::rptMsg("  ".$val." -> ".$data);
					}
				}
				else {
					::rptMsg($key_path.$access." has no values.");
				}
			}
			else {
				::rptMsg($key_path.$access." not found.");
			}
			::rptMsg("");
# Attempt to retrieve PowerPoint docs			
			my $ppt = 'PowerPoint\\File MRU';
			if (my $ppt_key = $of_key->get_subkey($ppt)) {
				::rptMsg($key_path."\\".$ppt);
				::rptMsg("LastWrite Time ".gmtime($ppt_key->get_timestamp())." (UTC)");
				my @vals = $ppt_key->get_list_of_values();
				if (scalar(@vals) > 0) {
					my %files;
# Retrieve values and load into a hash for sorting			
					foreach my $v (@vals) {
						my $val = $v->get_name();
						if ($val eq "Max Display") { next; }
						my $data = getWinTS($v->get_data());
						my $tag = (split(/Item/,$val))[1];
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