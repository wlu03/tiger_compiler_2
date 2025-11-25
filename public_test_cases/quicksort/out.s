.text
main:
  move $fp, $sp
  addi $sp, $sp, -432
  sw $s3, 0($sp)
  sw $s2, 4($sp)
  sw $s1, 8($sp)
  sw $s0, 12($sp)
  li $s2, 0
  li $v0, 5
  syscall
  move $s0, $v0
  li $t9, 100
  bgt $s0, $t9, return_main
  li $t9, 1
  sub $s0, $s0, $t9
  li $s1, 0
loop0_main:
  bgt $s1, $s0, exit0_main
  li $v0, 5
  syscall
  move $s2, $v0
  sll $t8, $s1, 2
  addi $t7, $fp, 0
  sub $t6, $t7, $t8
  sw $s2, 0($t6)
  addi $s1, $s1, 1
  j loop0_main
exit0_main:
  move $a0, $fp
  addi $a0, $a0, 0
  li $a1, 0
  move $a2, $s0
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal quicksort
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  addi $sp, $sp, 0
  li $s1, 0
loop1_main:
  bgt $s1, $s0, exit1_main
  sll $t8, $s1, 2
  addi $t7, $fp, 0
  sub $t6, $t7, $t8
  lw $s2, 0($t6)
  move $a0, $s2
  li $v0, 1
  syscall
  li $a0, 10
  li $v0, 11
  syscall
  addi $s1, $s1, 1
  j loop1_main
exit1_main:
return_main:
  lw $s3, 0($sp)
  lw $s2, 4($sp)
  lw $s1, 8($sp)
  lw $s0, 12($sp)
  addi $sp, $sp, 432
  li $v0, 10
  syscall
quicksort:
  move $fp, $sp
  addi $sp, $sp, -64
  sw $s5, 0($sp)
  sw $s6, 4($sp)
  sw $s4, 8($sp)
  sw $s7, 12($sp)
  sw $s3, 16($sp)
  sw $s1, 20($sp)
  sw $s0, 24($sp)
  sw $s2, 28($sp)
  move $s5, $a0
  move $s4, $a1
  move $s6, $a2
  li $s2, 0
  li $s0, 0
  bge $s4, $s6, end_quicksort
  add $s0, $s4, $s6
  li $t9, 2
  div $s0, $s0, $t9
  sll $t8, $s0, 2
  sub $t6, $s5, $t8
  lw $s1, 0($t6)
  li $t9, 1
  sub $s2, $s4, $t9
  addi $s0, $s6, 1
loop0_quicksort:
loop1_quicksort:
  addi $s2, $s2, 1
  sll $t8, $s2, 2
  sub $t6, $s5, $t8
  lw $s3, 0($t6)
  move $s7, $s3
  blt $s7, $s1, loop1_quicksort
loop2_quicksort:
  li $t9, 1
  sub $s0, $s0, $t9
  sll $t8, $s0, 2
  sub $t6, $s5, $t8
  lw $s3, 0($t6)
  bgt $s3, $s1, loop2_quicksort
  bge $s2, $s0, exit0_quicksort
  sll $t8, $s0, 2
  sub $t6, $s5, $t8
  sw $s7, 0($t6)
  sll $t8, $s2, 2
  sub $t6, $s5, $t8
  sw $s3, 0($t6)
  j loop0_quicksort
exit0_quicksort:
  addi $s1, $s0, 1
  addi $sp, $sp, -12
  sw $a0, 0($sp)
  sw $a1, 4($sp)
  sw $a2, 8($sp)
  move $a0, $s5
  move $a1, $s4
  move $a2, $s0
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal quicksort
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  lw $a0, 0($sp)
  lw $a1, 4($sp)
  lw $a2, 8($sp)
  addi $sp, $sp, 12
  addi $s0, $s0, 1
  addi $sp, $sp, -12
  sw $a0, 0($sp)
  sw $a1, 4($sp)
  sw $a2, 8($sp)
  move $a0, $s5
  move $a1, $s0
  move $a2, $s6
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal quicksort
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  lw $a0, 0($sp)
  lw $a1, 4($sp)
  lw $a2, 8($sp)
  addi $sp, $sp, 12
end_quicksort:
  lw $s5, 0($sp)
  lw $s6, 4($sp)
  lw $s4, 8($sp)
  lw $s7, 12($sp)
  lw $s3, 16($sp)
  lw $s1, 20($sp)
  lw $s0, 24($sp)
  lw $s2, 28($sp)
  addi $sp, $sp, 64
  jr $ra

