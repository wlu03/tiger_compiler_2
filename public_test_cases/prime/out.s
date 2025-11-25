.text
main:
  move $fp, $sp
  addi $sp, $sp, -88
  sw $s6, 0($sp)
  sw $s3, 4($sp)
  sw $s5, 8($sp)
  sw $s0, 12($sp)
  sw $s4, 16($sp)
  sw $s2, 20($sp)
  sw $s1, 24($sp)
  li $s5, 0
  li $s3, 2
  li $s5, 3
  li $s0, 6
  li $s0, 0
  li $v0, 5
  syscall
  move $s4, $v0
  li $t9, 1
  bgt $s4, $t9, label0_main
  li $s6, 0
  j print_main
label0_main:
  li $t9, 3
  bgt $s4, $t9, label1_main
  li $s6, 1
  j print_main
label1_main:
  move $a0, $s4
  move $a1, $s3
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal divisible
  move $s3, $v0
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  addi $sp, $sp, 0
  move $s6, $s0
  li $t9, 1
  beq $s3, $t9, label2_main
  move $a0, $s4
  move $a1, $s5
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal divisible
  move $s3, $v0
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  addi $sp, $sp, 0
  move $s6, $s0
  li $t9, 1
  beq $s3, $t9, label2_main
  j label3_main
label2_main:
  j print_main
label3_main:
  li $s5, 5
loop_main:
  mul $s3, $s5, $s5
  bgt $s3, $s4, exit_main
  move $a0, $s4
  move $a1, $s5
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal divisible
  move $s3, $v0
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  addi $sp, $sp, 0
  move $s6, $s0
  li $s2, 0
  li $s1, 0
  li $t9, 1
  beq $s3, $t9, label2_main
  addi $s3, $s5, 2
  move $a0, $s4
  move $a1, $s3
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal divisible
  move $s3, $v0
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  addi $sp, $sp, 0
  move $s6, $s0
  li $t9, 1
  beq $s3, $t9, label2_main
  addi $s5, $s5, 6
  j loop_main
exit_main:
  move $s0, $s2
  move $s6, $s1
  li $s6, 1
print_main:
  move $a0, $s6
  li $v0, 1
  syscall
  li $a0, 10
  li $v0, 11
  syscall
  lw $s6, 0($sp)
  lw $s3, 4($sp)
  lw $s5, 8($sp)
  lw $s0, 12($sp)
  lw $s4, 16($sp)
  lw $s2, 20($sp)
  lw $s1, 24($sp)
  addi $sp, $sp, 88
  li $v0, 10
  syscall
divisible:
  move $fp, $sp
  addi $sp, $sp, -16
  sw $s2, 0($sp)
  sw $s1, 4($sp)
  sw $s0, 8($sp)
  move $s1, $a0
  move $s0, $a1
  div $s2, $s1, $s0
  mul $s2, $s2, $s0
  bne $s1, $s2, label0_divisible
  li $v0, 1
  lw $s2, 0($sp)
  lw $s1, 4($sp)
  lw $s0, 8($sp)
  addi $sp, $sp, 16
  jr $ra
label0_divisible:
  li $v0, 0
  lw $s2, 0($sp)
  lw $s1, 4($sp)
  lw $s0, 8($sp)
  addi $sp, $sp, 16
  jr $ra

