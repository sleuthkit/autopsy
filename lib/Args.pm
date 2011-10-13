#
# Functions to check and get the arguments from URL
#
# ver 2.00+
# Brian Carrier [carrier@sleuthkit.org]
# Copyright (c) 2003-2004 by Brian Carrier.  All rights reserved
#
# This file is part of the Autopsy Forensic Browser (Autopsy)
#
# Autopsy is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.
#
# Autopsy is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with Autopsy; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
#
# THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR IMPLIED
# WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR ANY PARTICULAR PURPOSE.
# IN NO EVENT SHALL THE AUTHORS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
# INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
# (INCLUDING, BUT NOT LIMITED TO, LOSS OF USE, DATA, OR PROFITS OR
# BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
# WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
# OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
# ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package Args;

# Parse the argument string into the args hash
sub parse_args {
    my $lcl_args = shift;
    foreach my $nam_val (split(/&/, $lcl_args)) {
        my ($name, $value) = split(/=/, $nam_val);
        if (defined $value) {
            my $dec_name = url_decode($name);
            $Args::enc_args{$dec_name} = $value;
            $Args::args{$dec_name}     = url_decode($value);
        }
    }
}

sub url_encode {
    my $text = shift;
    $text =~ s/([^a-z0-9_.!~*'() -])/sprintf "%%%02X", ord($1)/eig;
    $text =~ tr/ /+/;
    return $text;
}

sub url_decode {
    my $text = shift;
    $text =~ tr/\+/ /;
    $text =~ s/%([a-f0-9][a-f0-9])/chr( hex( $1 ) )/eig;
    return $text;
}

# This assumes that the checking of the types has been done and this just
# makes a string of the key values if they exist
#
#  case
#  host
#  img

# Must add & after
sub make_baseargs {
    $Args::baseargs = "";

    # The standard case, host, and investigator
    $Args::baseargs .= "case=$Args::enc_args{'case'}&"
      if ((exists $Args::enc_args{'case'}) && ($Args::enc_args{'case'} ne ""));
    $Args::baseargs .= "host=$Args::enc_args{'host'}&"
      if ((exists $Args::enc_args{'host'}) && ($Args::enc_args{'host'} ne ""));
    $Args::baseargs .= "inv=$Args::enc_args{'inv'}&"
      if ((exists $Args::enc_args{'inv'}) && ($Args::enc_args{'inv'} ne ""));

    $Args::baseargs_novol = $Args::baseargs;

    # Add the image, file system type, and mount point
    $Args::baseargs .= "vol=$Args::enc_args{'vol'}&"
      if ((exists $Args::enc_args{'vol'}) && ($Args::enc_args{'vol'} ne ""));

    # remove the final '&'
    $Args::baseargs_novol = $1 if ($Args::baseargs_novol =~ /^(.*?)&$/);
    $Args::baseargs       = $1 if ($Args::baseargs       =~ /^(.*?)&$/);

    return;
}

# Does not do mnt or img
sub make_hidden {
    my $str = "";

    $str .=
      "<input type=\"hidden\" name=\"host\" value=\"$Args::args{'host'}\">\n"
      if ((exists $Args::args{'host'}) && ($Args::args{'host'} ne ""));

    $str .=
      "<input type=\"hidden\" name=\"case\" value=\"$Args::args{'case'}\">\n"
      if ((exists $Args::args{'case'}) && ($Args::args{'case'} ne ""));

    $str .=
      "<input type=\"hidden\" name=\"inv\" value=\"$Args::args{'inv'}\">\n"
      if ((exists $Args::args{'inv'}) && ($Args::args{'inv'} ne ""));

    return $str;
}

###############################
# block
###############################
sub check_block {
    if ((!exists $Args::args{'block'}) || ($Args::args{'block'} !~ /^\d+$/)) {
        Print::print_check_err(
            "Invalid block argument (positive numbers only)");
    }
    return 0;
}

sub get_block {
    if ($Args::args{'block'} =~ /^(\d+)$/) {
        return $1;
    }
    Print::print_err("Invalid Block");
}

###############################
# body
###############################

sub check_body {
    unless (exists $Args::args{'body'}) {
        Print::print_check_err("Missing body argument");
    }
    unless ($Args::args{'body'} =~ /^$::REG_VNAME$/o) {
        Print::print_check_err(
            "Invalid body value (only letters, " . "numbers,-,., and _)");
    }
    return 0;
}

sub get_body {
    if ($Args::args{'body'} =~ /^($::REG_VNAME)$/o) {
        return $1;
    }
    Print::print_err("Invalid Body");
}

################################
# Case name
################################

sub check_case {
    unless (exists $Args::args{'case'}) {
        Print::print_check_err("Missing case argument");
    }
    unless ($Args::args{'case'} =~ /^$::REG_CASE$/o) {
        Print::print_check_err(
            "Invalid case value (letters, num, and symbols only");
    }
    return 0;
}

sub get_case {
    if ($Args::args{'case'} =~ /^($::REG_CASE)$/o) {
        return $1;
    }
    Print::print_err("Invalid Case Name");
}

###############################
# cell_mode
###############################
sub check_cell_mode {
    if (   (!exists $Args::args{'cell_mode'})
        || ($Args::args{'cell_mode'} !~ /^\d$/o))
    {
        Print::print_check_err(
            "Invalid cell_mode argument (numbers >= 0 only)");
    }
    return 0;
}

################################
# dir
################################
sub check_dir {
    if (   (!exists $Args::args{'dir'})
        || ($Args::args{'dir'} =~ /\/\.\.\//)
        || ($Args::args{'dir'} =~ /\;/))
    {
        Print::print_check_err("Invalid dir argument (valid file path only)");
    }
    return 0;
}

sub get_dir {
    if ($Args::args{'dir'} =~ /([^;]*)/o) {
        my $d = $1;

        # Remove double slashes
        $d =~ s/\/\//\//g;
        return $d;
    }
    Print::print_err("Invalid Directory");
}

###############################
# dirmode
###############################
sub check_dirmode {
    if ((!exists $Args::args{'dirmode'}) || ($Args::args{'dirmode'} !~ /^\d+$/))
    {
        Print::print_check_err(
            "Invalid dirmode argument (positive numbers only)");
    }
    return 0;
}

sub get_dirmode {
    if ($Args::args{'dirmode'} =~ /^(\d+)$/) {
        return $1;
    }
    Print::print_err("Invalid dirmode");
}

################################
# do_md5
################################
sub check_do_md5 {
    if ((!exists $Args::args{'do_md5'}) || ($Args::args{'do_md5'} !~ /^\d+$/)) {
        Print::print_check_err("Missing do_md5 argument");
    }
    return 0;
}

sub get_do_md5 {
    if ($Args::args{'do_md5'} =~ /^\s*(\d+)$/) {
        return $1;
    }
    Print::print_err("Invalid MD5 Flag");
}

################################
# fname
################################

sub check_fname {
    unless (exists $Args::args{'fname'}) {
        Print::print_check_err("Missing fname argument");
    }
    unless ($Args::args{'fname'} =~ /^$::REG_FNAME$/o) {
        Print::print_check_err(
            "Invalid fname value (only letters, " . "numbers,-,., and _)");
    }
    return 0;
}

sub get_fname {
    if ($Args::args{'fname'} =~ /^($::REG_FNAME)$/o) {
        return "$::host_dir" . "$::DATADIR/$1";
    }
    Print::print_err("Invalid File Name");
}

################################
# fname_mode
################################
sub check_fname_mode {
    if (!exists $Args::args{'fname_mode'}) {
        Print::print_check_err("Missing fname_mode argument");
    }
    unless ($Args::args{'fname_mode'} =~ /^\d+$/) {
        Print::print_check_err("invalid mode: numbers only");
    }
    return 0;
}

################################
# fname_rel
# Return the relative fname
################################
sub get_fname_rel {
    if ($Args::args{'fname'} =~ /^($::REG_FNAME)$/o) {
        return "$::DATADIR/$1";
    }
    Print::print_err("Invalid Relative File Name");
}

###############################
# force
###############################
sub get_force {
    if ($Args::args{'force'} =~ /^(\d+)$/) {
        return $1;
    }
    Print::print_err("Invalid Force Flag");
}

################################
# ftype
################################
sub get_ftype_blah {
    if (exists $Args::args{'ftype'}) {
        if ($Args::args{'ftype'} =~ /^($::REG_FTYPE)$/o) {
            return $1;
        }
    }
    if (   (exists $Args::args{'img'})
        && (exists $Caseman::vol2ftype{$Args::args{'img'}}))
    {
        return $Caseman::vol2ftype{$Args::args{'img'}};
    }
    Print::print_err("Missing ftype value");
}

sub check_ftype_blah {
    unless (
        (
               (exists $Args::args{'img'})
            && (exists $Caseman::vol2ftype{$Args::args{'img'}})
            && ($Caseman::vol2ftype{$Args::args{'img'}} =~ /^$::REG_FTYPE$/o)
        )
        || (   (exists $Args::args{'ftype'})
            && ($Args::args{'ftype'} =~ /^$::REG_FTYPE$/o))
      )
    {
        Print::print_check_err("Missing or invalid ftype value");
    }
    return 0;
}

################################
# host
# Host for the case
################################

sub check_host {
    unless (exists $Args::args{'host'}) {
        Print::print_check_err("Missing host argument");
    }
    unless ($Args::args{'host'} =~ /^$::REG_HOST$/o) {
        Print::print_check_err("Invalid host value");
    }
    return 0;
}

sub get_host {
    if ($Args::args{'host'} =~ /^($::REG_HOST)$/o) {
        return $1;
    }
    Print::print_err("Invalid Host");
}

################################
# htype
################################
sub check_htype {
    if ((!exists $Args::args{'htype'}) || ($Args::args{'htype'} !~ /^\d+$/)) {
        Print::print_check_err(
            "Invalid htype argument (positive numbers only)");
    }
    return 0;
}

sub get_htype {
    if ($Args::args{'htype'} =~ /^(\d+)$/) {
        return $1;
    }
    Print::print_err("Invalid htype");
}

###############################
# ifind
# ifind is optional and by default is 0 if not given
###############################
sub get_ifind {
    if (!exists $Args::args{'ifind'}) {
        return 0;
    }
    elsif ($Args::args{'ifind'} =~ /^(\d+)$/) {
        return $1;
    }
    Print::print_err("Invalid ifind flag");
}

###############################
# img_path is used when adding images - it is the full path to the
# non-evidence locker copy of the image
###############################

sub check_img_path {
    if (!exists $Args::args{'img_path'}) {
        Print::print_check_err("Missing image (img_path) argument");
    }
    elsif ($Args::args{'img_path'} =~ /^$::REG_IMG_PATH$/o) {

        # Check for its actual existence

        Print::print_check_err("Image not found at $Args::args{'img_path'}")
          unless (
            (-e "$Args::args{'img_path'}")
            || (   (-l "$Args::args{'img_path'}")
                && (-e readlink "$::host_dir" . "$Args::args{$img}"))
          );
    }
    else {
        Print::print_check_err("Invalid image path (only letters, "
              . "numbers,-,.,_/ and start with /) [$Args::args{'img_path'}]");
    }

    return 0;
}

sub get_img_path {
    if ($Args::args{'img_path'} =~ /^($::REG_IMG_PATH)$/o) {
        return "$1";
    }
    Print::print_err("Invalid Image Path");
}

sub check_img_path_wild {
    if (!exists $Args::args{'img_path'}) {
        Print::print_check_err("Missing  wild image (img_path) argument");
    }
    elsif ($Args::args{'img_path'} !~ /^$::REG_IMG_PATH_WILD$/o) {

        # IF there is extra white space then remove it and move on
        if ($Args::args{'img_path'} =~ /^\s*($::REG_IMG_PATH_WILD)\s*$/o) {
            $Args::args{'img_path'} = $1;
            return 0;
        }
        else {
            Print::print_check_err("Invalid wild image (img_path) argument");
        }
    }

    return 0;
}

sub get_img_path_wild {
    if ($Args::args{'img_path'} =~ /^($::REG_IMG_PATH_WILD)$/o) {
        return "$1";
    }
    Print::print_err("Invalid Image Path");
}

###############################
# meta
###############################

sub check_meta {
    my $meta = shift;
    if (   (!exists $Args::args{$meta})
        || ($Args::args{$meta} !~ /^$::REG_META$/o))
    {
        Print::print_check_err(
            "Invalid meta address ($meta) argument (numbers >= 0 only)");
    }
    return 0;
}

sub get_meta {
    my $meta = shift;
    if ($Args::args{$meta} =~ /^($::REG_META)$/o) {
        return $1;
    }
    Print::print_err("Invalid Meta Address");
}

################################
# inv
# Investigator
################################

sub check_inv {
    unless (exists $Args::args{'inv'}) {
        Print::print_check_err("Missing inv argument");
    }
    unless ($Args::args{'inv'} =~ /^$::REG_INVESTIG$/o) {
        Print::print_check_err(
            "Invalid inv value (letters, num, and symbols only");
    }
    return 0;
}

sub get_inv {
    if ($Args::args{'inv'} =~ /^($::REG_INVESTIG)$/o) {
        return $1;
    }
    Print::print_err("Invalid Investigator");
}

###############################
# len
###############################
sub check_len {
    if (   (!exists $Args::args{'len'})
        || ($Args::args{'len'} !~ /^\d+$/)
        || ($Args::args{'len'} == 0))
    {
        Print::print_check_err("Invalid len argument (positive numbers only)");
    }
    return 0;
}

sub get_len {
    if ((exists $Args::args{'len'}) && ($Args::args{'len'} =~ /^(\d+)$/)) {
        return $1;
    }

    # return the default len of 1 if it is not defined
    return 1;
}

###############################
# min
###############################
sub get_min {
    if ($Args::args{'min'} =~ /^(\d+)$/) {
        return $1;
    }
    Print::print_err("Invalid Minute");
}

################################
# module
################################
sub check_mod {
    if ((!exists $Args::args{'mod'}) || ($Args::args{'mod'} !~ /^\d+$/)) {
        Print::print_check_err(
            "Invalid Module argument (positive numbers only)");
    }
    return 0;
}

sub get_mod {
    if ($Args::args{'mod'} =~ /^(\d+)$/) {
        return $1;
    }
    Print::print_err("Invalid Module");
}

################################
# mnt
###############################

sub check_mnt {
    my $ftype = Args::get_ftype();
    if (($ftype eq "blkls") || ($ftype eq "swap") || ($ftype eq "raw")) {
        $Args::args{'mnt'}     = $ftype;
        $Args::enc_args{'mnt'} = $ftype;
    }
    elsif (!exists $Args::args{'mnt'}) {

        # Look it up if it is not found
        if (exists $Args::args{'img'}) {
            unless (exists $Caseman::vol2mnt{$Args::args{'img'}}) {
                Print::print_check_err(
                    "Mounting point not found: $Args::args{'img'}");
            }
            my $mnt = $Caseman::vol2mnt{$Args::args{'img'}};
            $Args::args{'mnt'}     = $mnt;
            $Args::enc_args{'mnt'} = Args::url_encode($mnt);
        }
        else {
            Print::print_check_err("Mounting point not found");
        }
    }
    if ($Args::args{'mnt'} =~ /\/\.\.\//) {
        Print::print_check_err(
            "Invalid mount point argument (valid file path only)");
    }
    unless ($Args::args{'mnt'} =~ /^$::REG_MNT$/o) {
        Print::print_check_err(
            "Invalid mount point argument (valid file path only)");
    }
    return 0;
}

sub get_mnt {
    if ((exists $Args::args{'mnt'}) && ($Args::args{'mnt'} =~ /($::REG_MNT)/o))
    {
        return $1;
    }
    Print::print_err("Invalid Mounting Point");
}

################################
# note
################################
sub check_note {
    if (!exists $Args::args{'note'}) {
        Print::print_check_err("Missing note argument");
    }
    return 0;
}

#################
# num_img - adding disk images

sub check_num_img {
    if ((!exists $Args::args{'num_img'}) || ($Args::args{'num_img'} !~ /^\d+$/))
    {
        Print::print_check_err(
            "Invalid num_img argument (positive numbers only)");
    }
    return 0;
}

sub get_num_img {
    if ($Args::args{'num_img'} =~ /^(\d+)$/) {
        return $1;
    }
    Print::print_err("Invalid num_img");
}

###############################
# recmode
###############################
sub check_recmode {
    if ((!exists $Args::args{'recmode'}) || ($Args::args{'recmode'} !~ /^\d+$/))
    {
        Print::print_check_err(
            "Invalid recmode argument (positive numbers only)");
    }
    return 0;
}

sub get_recmode {
    if ($Args::args{'recmode'} =~ /^(\d+)$/) {
        return $1;
    }
    Print::print_err("Invalid recmode");
}

################################
# srchidx
#
# Index for previous keyword search
###############################
sub check_srchidx {
    if ((!exists $Args::args{'srchidx'}) || ($Args::args{'srchidx'} !~ /^\d+$/))
    {
        Print::print_check_err(
            "Invalid srchidx argument (positive numbers only)");
    }
    return 0;
}

###############################
# sort
###############################
sub check_sort {
    if ((!exists $Args::args{'sort'}) || ($Args::args{'sort'} !~ /^\d+$/)) {
        Print::print_check_err("Invalid sort argument (positive numbers only)");
    }
    return 0;
}

sub get_sort {
    if ($Args::args{'sort'} =~ /^(\d+)$/) {
        return $1;
    }
    Print::print_err("Invalid sort flag");
}

################################
# st_mon
################################
sub check_st_mon {
    if (   (exists $Args::args{'st_mon'})
        && ($Args::args{'st_mon'} =~ /^(\d\d?)$/))
    {
        if (($1 < 1) || ($1 > 12)) {
            print("Invalid start month\n");
            return 1;
        }
    }
    else {
        print("Invalid start month\n");
        return 1;
    }
}

sub get_st_mon {
    if ($Args::args{'st_mon'} =~ /^(\d\d?)$/) {
        return $1;
    }
    Print::print_err("Invalid Month");
}

################################
# st_year
################################
sub check_st_year {
    if (   (exists $Args::args{'st_year'})
        && ($Args::args{'st_year'} =~ /^(\d\d\d\d?)$/))
    {
        if (($1 < 1970) || ($1 > 2020)) {
            print("Invalid start year\n");
            return 1;
        }
    }
    else {
        print("Invalid start year\n");
        return 1;
    }
}

sub get_st_year {
    if ($Args::args{'st_year'} =~ /^(\d\d\d\d)$/) {
        return $1;
    }
    Print::print_err("Invalid Year");
}

################################
# str
# search string
################################
# This should be made more flexible
sub check_str {
    if (!exists $Args::args{'str'}) {
        Print::print_check_err("Missing string argument");
    }
    return 0;
}

sub get_str {
    if ($Args::args{'str'} =~ /^\s*(.*)$/) {
        return $1;
    }
    Print::print_err("Invalid String");
}

###############################
# submod
# Used by the tab module to identify the actual module
###############################
sub check_submod {
    if ((!exists $Args::args{'submod'}) || ($Args::args{'submod'} !~ /^\d+$/)) {
        Print::print_check_err(
            "Invalid sub-mode argument (positive numbers only)");
    }
    return 0;
}

sub get_submod {
    if ($Args::args{'submod'} =~ /^(\d+)$/) {
        return $1;
    }
    Print::print_err("Invalid sub-mode");
}

################################
# tl
###############################
sub check_tl {
    if ((!exists $Args::args{'tl'}) || ($Args::args{'tl'} !~ /^$::REG_VNAME$/))
    {
        Print::print_check_err(
            "Invalid timeline argument (positive numbers only)");
    }
    return 0;
}

sub get_tl {
    if ($Args::args{'tl'} =~ /^($::REG_VNAME)$/o) {
        return $1;
    }
    Print::print_err("Invalid Timeline");
}

################################
# ts
# time skew
################################
sub check_ts {
    if ((!exists $Args::args{'ts'}) || ($Args::args{'ts'} !~ /^$::REG_SKEW$/o))
    {
        Print::print_check_err("Missing time skew argument");
    }
    return 0;
}

sub get_ts {
    if ($Args::args{'ts'} =~ /^\s*($::REG_SKEW)$/o) {
        return $1;
    }
    Print::print_err("Invalid Time Skew");
}

################################
# tz
# timezone
################################
sub check_tz {
    if (   (!exists $Args::args{'tz'})
        || ($Args::args{'tz'} !~ /^$::REG_ZONE_ARGS$/o))
    {
        Print::print_check_err("Missing time zone argument");
    }
    return 0;
}

sub get_tz {
    if ($Args::args{'tz'} =~ /^($::REG_ZONE_ARGS)$/o) {
        return $1;
    }
    Print::print_err("Invalid Timezone");
}

################################
# unitsize
################################
sub get_unitsize {

    my $vol   = Args::get_vol('vol');
    my $ftype = $Caseman::vol2ftype{$vol};
    my $blkcat_out;

    if ($ftype eq 'blkls') {
        if (exists $Caseman::mod2vol{$vol}) {
            my $orig    = $Caseman::mod2vol{$vol};
            my $img     = $Caseman::vol2path{$orig};
            my $offset  = $Caseman::vol2start{$orig};
            my $imgtype = $Caseman::vol2itype{$orig};

            local *OUT;
            Exec::exec_pipe(*OUT,
"'$::TSKDIR/blkcat' -f $Caseman::vol2ftype{$orig} -s -o $offset -i $imgtype $img"
            );
            $blkcat_out = <OUT>;
            close(OUT);
        }

        # We don't have the original image, so just set the size to 512
        else {
            return 512;
        }
    }
    elsif ($ftype eq 'swap') {
        return 4096;
    }
    elsif ($ftype eq 'raw') {
        return 512;
    }
    elsif ($Caseman::vol2cat{$vol} eq 'disk') {
        return 512;
    }
    else {
        my $img     = $Caseman::vol2path{$vol};
        my $offset  = $Caseman::vol2start{$vol};
        my $imgtype = $Caseman::vol2itype{$vol};

        local *OUT;
        Exec::exec_pipe(*OUT,
            "'$::TSKDIR/blkcat' -f $ftype -s -o $offset -i $imgtype $img");
        $blkcat_out = <OUT>;
        close(OUT);
    }
    $blkcat_out = "Error getting unit size"
      if ((!defined $blkcat_out) || ($blkcat_out eq ""));

    if ($blkcat_out =~ /(\d+): Size of Addressable Unit/) {
        return $1;
    }
    else {
        Print::print_err("Error identifying block size (blkcat -s output)\n"
              . "$blkcat_out\n");
    }
}

################################
# View - subset of module
################################
sub check_view {
    if ((!exists $Args::args{'view'}) || ($Args::args{'view'} !~ /^\d+$/)) {
        Print::print_check_err("Invalid View argument (positive numbers only)");
    }
    return 0;
}

sub get_view {
    if ($Args::args{'view'} =~ /^(\d+)$/) {
        return $1;
    }
    Print::print_err("Invalid View");
}

###############################
# We don't allow much for the volume because this is an argument to
# the TSK programs.  We keep these files only in one
# directory and for easy/simple security only allow basic names
# Symbolic links are allowed if these simple names are not desired
#
# Allowed values are A-Za-z0-9_-.
#
# The argument is the name of the image
###############################

sub check_vol {
    my $vol = shift;
    if ((!exists $Args::args{$vol}) || ($Args::args{$vol} !~ /^$::REG_VNAME$/))
    {
        Print::print_check_err(
            "Invalid volume argument (name and number only)");
    }
    return 0;
}

sub get_vol {
    my $vol = shift;
    if ($Args::args{$vol} =~ /^($::REG_VNAME)$/) {
        return $1;
    }
    Print::print_err("Invalid volume ($vol)");
}

1;
