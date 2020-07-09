#-----------------------------------------------------------
# webroot.pl
#   Plugin to parse webroot antivirus registry data
#	I have only extracted some of the data from the root key "WOW6432Node\\WRData", manual review is recommended
# 	I also do not know what a number of fields mean, so further work may be required to fully exploit the data in this key.
#
# Change history
#   20191230 - initial commit
#
# References
# 
# copyright 2019 Phill Moore
#-----------------------------------------------------------

package webroot;
use strict;


my %config = (hive          => "SOFTWARE",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20191230);

sub getConfig{return %config}
sub getShortDescr {
	return "Provides *some* of the webroot data in the registry, manual review is still recommended. Particularly surrounding the root key";
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}


sub displayActions {
	::rptMsg("---------------------------------------------------------------");
	my $root_key = shift;
	my $key_path = "WOW6432Node\\WRData\\Actions";
	my $key;
	if ($key = $root_key->get_subkey($key_path)){
		::rptMsg("");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		my @vals = $key->get_list_of_values();
		
		foreach my $val (@vals) {
			my $d = $val->get_data();
			my $v = $val->get_name();
			my $str = $v.":\t".$d;
			::rptMsg($str);
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub displayJournal {
	::rptMsg("---------------------------------------------------------------");
	my $root_key = shift;
	my $key_path = "WOW6432Node\\WRData\\Journal";
	my $key;
	if ($key = $root_key->get_subkey($key_path)){
		::rptMsg("");
		::rptMsg($key_path . " - ". gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		my @vals = $key->get_list_of_values();
		
		::rptMsg("filename,md5,timestamp"); 
		foreach my $val (@vals) {
		
			#format = "filename=$filename,md5=$md5,timestamp=$timestamp"
			my @d = split (/,/, $val->get_data());
			my $fn=(split(/\=/,$d[0]))[1];
			my $md5= (split(/\=/,$d[1]))[1];
			my $ts=(split(/\=/,$d[2]))[1];
			my $timestamp=gmtime($ts);
			my $str = $fn.",".$md5.",".$ts.",".$timestamp;
			::rptMsg($str);
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub displayStatus {
	::rptMsg("---------------------------------------------------------------");
	my $root_key = shift;
	my $key_path = "WOW6432Node\\WRData\\Status";
	my $key;
	if ($key = $root_key->get_subkey($key_path)){
		::rptMsg("");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		my @vals = $key->get_list_of_values();
		
		foreach my $val (@vals) {
			my $d = $val->get_data();
			my $v = $val->get_name();
			
			#if $v is in the following list then convert timestamp 
			my @timestamp_fields = ["AgentStartupTime", "ExpirationDate", "LastDeepScan", "LastScan", "LastThreatSeen", "SystemStateUpdated", "UpdateTime", "UpdateTime"];
			$d = $d." (".gmtime($d).")" if ($v ~~ @timestamp_fields);
		
			my $str = $v.":\t".$d;
			::rptMsg($str);
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub displayFileFlags {
	::rptMsg("---------------------------------------------------------------");
	my $root_key = shift;
	my $key_path = "WOW6432Node\\WRData\\FileFlags";
	my $key;
	if ($key = $root_key->get_subkey($key_path)){
		::rptMsg("");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		::rptMsg("MD5 hash:\t\t\t\taction, last changed");
		my @vals = $key->get_list_of_values();
		foreach my $val (@vals) {
			my $d = $val->get_data();
			my $v = $val->get_name();

			my @split_d = split (/\,/, $d);
			my @changetime = split (/\=/, $split_d[1]);
			my $str = $v.":\t".$d."(".gmtime($changetime[1]).")";
			::rptMsg($str);
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub displayIPM {
	::rptMsg("---------------------------------------------------------------");
	my $root_key = shift;
	my $key_path = "WOW6432Node\\WRData\\IPM";;
	my $key;
	if ($key = $root_key->get_subkey($key_path)){
		::rptMsg("");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		my @vals = $key->get_list_of_values();
		foreach my $val (@vals) {
			my $d = $val->get_data();
			my $v = $val->get_name();
			my $d = $d." (".gmtime($d).")"if ($v eq "ILU");
			my $str = $v.":\t".$d;
			::rptMsg($str);
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}




sub dumpAllVals {
	::rptMsg("---------------------------------------------------------------");
	my $root_key = shift;
	my $key_path = shift;
	my $key;
	if ($key = $root_key->get_subkey($key_path)){
		::rptMsg("");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		my @vals = $key->get_list_of_values();
		foreach my $val (@vals) {
			my $d = $val->get_data();
			my $v = $val->get_name();		
			my $str = $v.":\t".$d;
			::rptMsg($str);
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub dumpThreatsVals {
	::rptMsg("---------------------------------------------------------------");
	my $root_key = shift;
	my $key_path = shift;
	my $key;
	my $v;
	my $str;
	
	if ($key = $root_key->get_subkey($key_path)){
		::rptMsg("");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		my @vals = sort ($key->get_list_of_values());
		
		foreach my $val (@vals) {
			
			my $v = $val->get_name();
			my $d = $val->get_data();
			if ($v eq "Count"){
				$str = $v.":\t".$d;
			}
			else {
				my @split_d = split (/\|/, $d);
				my $path = $split_d[0];
				my $detection = $split_d[1];
				my $ts = $split_d[2];
				my $timestamp = gmtime(hex($ts));
				$str = $v.":\t".$path."|".$detection."|".$ts." (".$timestamp.")";
			}
			::rptMsg($str);
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	
	
}

sub displayThreats {
	my $root_key = shift;
	my $key_path = "WOW6432Node\\WRData\\Threats";

	
	dumpAllVals($root_key, $key_path);
	my @threats = ($key_path."\\Active", $key_path."\\History");
	
	foreach my $k (@threats){
		#::rptMsg($k);
		dumpThreatsVals($root_key, $k);
	}
}



my $VERSION = getVersion();
my $PLUGIN = "webroot";

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my $infected = 0;
	::logMsg("Launching ".$PLUGIN." v.".$VERSION);
    ::rptMsg($PLUGIN." v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
    
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "WOW6432Node\\WRData";
	my $key;
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		#my @vals = $key->get_list_of_values();
		my @vals = ("AVP", "BMV", "GWord", "HPL", "InstallDir", "InstalledVersion", "InstallTime", "LastInfection", "OIT");
		
		foreach my $v (@vals) {
			my $d = $key->get_value($v)->get_data();
			my $str = $v.":\t".$d;
			::rptMsg($str);
		}
		

		displayActions($root_key);
		displayFileFlags($root_key);
		displayIPM($root_key);
		displayJournal($root_key);
		displayStatus($root_key);
		displayThreats($root_key);		
		dumpAllVals($root_key, "WOW6432Node\\WRData\\wrURL");
	}
	else {
		::rptMsg($key_path." not found.");
		::rptMsg("");
	}
}1;