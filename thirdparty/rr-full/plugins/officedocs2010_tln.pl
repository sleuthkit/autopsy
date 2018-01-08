#-----------------------------------------------------------
# officedocs2010_tln.pl
#   Plugin to parse Office 2010 MRU entries (Word, Excel, Access, and PowerPoint)
#
# Change history
#   20120717 - created from officedocs2010.pl
#   20110901 - updated to remove dependency on the DateTime module
#   20010415 [fpi] * added this banner and change the name from "officedocs"
#                    to "officedocs2010", since this plugins is little different
#                    from Harlan's one (merging suggested)
#   20110830 [fpi] + banner, no change to the version number
#
# References
# 
# copyright 2011 Cameron Howell
# modified 20110901, H. Carvey keydet89@yahoo.com
#-----------------------------------------------------------

package officedocs2010_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20120717);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's Office 2010 doc MRU values; TLN output";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getWinTS {
	my $data = $_[0];
	my $winTS;
	my $dateTime;
	(my $prefix, my $suffix) = split(/\*/,$data);
	if ($prefix =~ /\[.{9}\]\[T(.{16})\]/) {
		$winTS = $1;
		my @vals = split(//,$winTS);
		my $t0 = join('',@vals[0..7]);
		my $t1 = join('',@vals[8..15]);
		$dateTime = ::getTime(hex($t1),hex($t0));
	}
#	return ($suffix ."  ". gmtime($dateTime));
	return ($suffix,$dateTime);
}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching officedocs2010 v.".$VERSION);
#  ::rptMsg("officedocs2010 v.".$VERSION); # 20110830 [fpi] + banner
#  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner

	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	# ::rptMsg("officedocs v.".$VERSION); # 20110830 [fpi] - redundant
	my $tag = 0;
	my $key_path = "Software\\Microsoft\\Office\\14.0";
	if (defined($root_key->get_subkey($key_path))) {
		$tag = 1;
	}
	
	if ($tag) {
#		::rptMsg("MSOffice version 2010 located.");
		my $key_path = "Software\\Microsoft\\Office\\14.0";	                 
		my $of_key = $root_key->get_subkey($key_path);
		if ($of_key) {
# Attempt to retrieve Word docs
			my $word = 'Word\\File MRU';
			if (my $word_key = $of_key->get_subkey($word)) {
#				::rptMsg($key_path."\\".$word);
#				::rptMsg("LastWrite Time ".gmtime($word_key->get_timestamp())." (UTC)");
				my @vals = $word_key->get_list_of_values();
				if (scalar(@vals) > 0) {
# Retrieve values and load into a hash for sorting			
					foreach my $v (@vals) {
						my $val = $v->get_name();
						if ($val eq "Max Display") { next; }
						my ($d0,$d1) = getWinTS($v->get_data());
						::rptMsg($d1."|REG|||OfficeDocs2010 - ".$d0);
					}
				}
				else {
#					::rptMsg($key_path.$word." has no values.");
				}
			}
			else {
#				::rptMsg($key_path.$word." not found.");
			}
#			::rptMsg("");
# Attempt to retrieve Excel docs
			my $excel = 'Excel\\File MRU';
			if (my $excel_key = $of_key->get_subkey($excel)) {
#				::rptMsg($key_path."\\".$excel);
#				::rptMsg("LastWrite Time ".gmtime($excel_key->get_timestamp())." (UTC)");
				my @vals = $excel_key->get_list_of_values();
				if (scalar(@vals) > 0) {
# Retrieve values and load into a hash for sorting			
					foreach my $v (@vals) {
						my $val = $v->get_name();
						if ($val eq "Max Display") { next; }
						my ($d0,$d1) = getWinTS($v->get_data());
						::rptMsg($d1."|REG|||OfficeDocs2010 - ".$d0);
					}
				}
				else {
#					::rptMsg($key_path.$excel." has no values.");
				}
			}
			else {
#				::rptMsg($key_path.$excel." not found.");
			}
#			::rptMsg("");
# Attempt to retrieve Access docs
			my $access = 'Access\\File MRU';
			if (my $access_key = $of_key->get_subkey($access)) {
#				::rptMsg($key_path."\\".$access);
#				::rptMsg("LastWrite Time ".gmtime($access_key->get_timestamp())." (UTC)");
				my @vals = $access_key->get_list_of_values();
				if (scalar(@vals) > 0) {
# Retrieve values and load into a hash for sorting			
					foreach my $v (@vals) {
						my $val = $v->get_name();
						if ($val eq "Max Display") { next; }
						my ($d0,$d1) = getWinTS($v->get_data());
						::rptMsg($d1."|REG|||OfficeDocs2010 - ".$d0);
					}
				}
				else {
#					::rptMsg($key_path."\\".$access." has no values.");
				}
			}
			else {
#				::rptMsg($key_path."\\".$access." not found.");
			}
#			::rptMsg("");
# Attempt to retrieve PowerPoint docs			
			my $ppt = 'PowerPoint\\File MRU';
			if (my $ppt_key = $of_key->get_subkey($ppt)) {
#				::rptMsg($key_path."\\".$ppt);
#				::rptMsg("LastWrite Time ".gmtime($ppt_key->get_timestamp())." (UTC)");
				my @vals = $ppt_key->get_list_of_values();
				if (scalar(@vals) > 0) {
# Retrieve values and load into a hash for sorting			
					foreach my $v (@vals) {
						my $val = $v->get_name();
						if ($val eq "Max Display") { next; }
						my ($d0,$d1) = getWinTS($v->get_data());
						::rptMsg($d1."|REG|||OfficeDocs2010 - ".$d0);
					}
				}
				else {
#					::rptMsg($key_path."\\".$ppt." has no values.");
				}		
			}
			else {
#				::rptMsg($key_path."\\".$ppt." not found.");
			}			
		}
		else {
#			::rptMsg("Could not access ".$key_path);
			::logMsg("Could not access ".$key_path);
		}
	}
	else {
		::logMsg("MSOffice version not found.");
#		::rptMsg("MSOffice version not found.");
	}
}

1;