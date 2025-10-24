.text
main:
  addi $sp, $sp, -432
  sw $ra, 428($sp)
  sw $fp, 424($sp)
  addi $fp, $sp, 424
  li $t1, 0
  li $v0, 5
  sw $t1, -408($fp)
  syscall
  move $t1, $v0
  li $t9, 100
  sw $t1, -416($fp)
  lw $t1, -416($fp)
  bgt $t1, $t9, return_main
  li $t9, 1
  lw $t1, -416($fp)
  sub $t1, $t1, $t9
  li $t2, 0
  sw $t2, -412($fp)
  sw $t1, -416($fp)
loop0_main:
  lw $t1, -412($fp)
  lw $t2, -416($fp)
  bgt $t1, $t2, exit0_main
  li $v0, 5
  syscall
  move $t1, $v0
  lw $t2, -412($fp)
  sll $t8, $t2, 2
  addi $t9, $fp, 0
  sub $t9, $t9, $t8
  sw $t1, 0($t9)
  addi $t2, $t2, 1
  sw $t1, -408($fp)
  sw $t2, -412($fp)
  j loop0_main
exit0_main:
  move $a0, $fp
  addi $a0, $a0, 0
  li $a1, 0
  lw $t1, -416($fp)
  move $a2, $t1
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal quicksort
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  addi $sp, $sp, 0
  li $t1, 0
  sw $t1, -412($fp)
loop1_main:
  lw $t1, -412($fp)
  lw $t2, -416($fp)
  bgt $t1, $t2, exit1_main
  lw $t1, -412($fp)
  sll $t8, $t1, 2
  addi $t9, $fp, 0
  sub $t9, $t9, $t8
  lw $t2, 0($t9)
  move $a0, $t2
  li $v0, 1
  sw $t2, -408($fp)
  syscall
  li $a0, 10
  li $v0, 11
  syscall
  lw $t1, -412($fp)
  addi $t1, $t1, 1
  sw $t1, -412($fp)
  j loop1_main
exit1_main:
return_main:
  li $v0, 10
  syscall
quicksort:
  addi $sp, $sp, -64
  sw $ra, 60($sp)
  sw $fp, 56($sp)
  addi $fp, $sp, 56
  move $t1, $a0
  move $t2, $a1
  move $t3, $a2
  li $t4, 0
  sw $t1, -8($fp)
  li $t1, 0
  sw $t3, -16($fp)
  sw $t2, -12($fp)
  sw $t4, -44($fp)
  sw $t1, -48($fp)
  lw $t1, -12($fp)
  lw $t2, -16($fp)
  bge $t1, $t2, end_quicksort
  lw $t1, -12($fp)
  lw $t2, -16($fp)
  add $t3, $t1, $t2
  li $t9, 2
  div $t3, $t3, $t9
  sll $t8, $t3, 2
  lw $t4, -8($fp)
  sub $t9, $t4, $t8
  lw $t4, 0($t9)
  li $t9, 1
  sub $t1, $t1, $t9
  addi $t2, $t2, 1
  sw $t3, -36($fp)
  sw $t4, -40($fp)
  sw $t1, -44($fp)
  sw $t2, -48($fp)
loop0_quicksort:
loop1_quicksort:
  lw $t1, -44($fp)
  addi $t1, $t1, 1
  sll $t8, $t1, 2
  lw $t2, -8($fp)
  sub $t9, $t2, $t8
  lw $t3, 0($t9)
  move $t4, $t3
  sw $t4, -20($fp)
  sw $t3, -32($fp)
  sw $t1, -44($fp)
  lw $t1, -20($fp)
  lw $t2, -40($fp)
  blt $t1, $t2, loop1_quicksort
loop2_quicksort:
  li $t9, 1
  lw $t1, -48($fp)
  sub $t1, $t1, $t9
  sll $t8, $t1, 2
  lw $t2, -8($fp)
  sub $t9, $t2, $t8
  lw $t3, 0($t9)
  move $t4, $t3
  sw $t4, -24($fp)
  sw $t3, -32($fp)
  sw $t1, -48($fp)
  lw $t1, -24($fp)
  lw $t2, -40($fp)
  bgt $t1, $t2, loop2_quicksort
  lw $t1, -44($fp)
  lw $t2, -48($fp)
  bge $t1, $t2, exit0_quicksort
  lw $t1, -48($fp)
  sll $t8, $t1, 2
  lw $t2, -8($fp)
  sub $t9, $t2, $t8
  lw $t3, -20($fp)
  sw $t3, 0($t9)
  lw $t4, -44($fp)
  sll $t8, $t4, 2
  sub $t9, $t2, $t8
  lw $t2, -24($fp)
  sw $t2, 0($t9)
  j loop0_quicksort
exit0_quicksort:
  lw $t1, -48($fp)
  addi $t2, $t1, 1
  addi $sp, $sp, -12
  sw $a0, 0($sp)
  sw $a1, 4($sp)
  sw $a2, 8($sp)
  lw $t3, -8($fp)
  move $a0, $t3
  lw $t4, -12($fp)
  move $a1, $t4
  move $a2, $t1
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  sw $t2, -28($fp)
  jal quicksort
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  lw $a0, 0($sp)
  lw $a1, 4($sp)
  lw $a2, 8($sp)
  addi $sp, $sp, 12
  lw $t1, -48($fp)
  addi $t1, $t1, 1
  addi $sp, $sp, -12
  sw $a0, 0($sp)
  sw $a1, 4($sp)
  sw $a2, 8($sp)
  lw $t2, -8($fp)
  move $a0, $t2
  move $a1, $t1
  lw $t3, -16($fp)
  move $a2, $t3
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  sw $t1, -48($fp)
  jal quicksort
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  lw $a0, 0($sp)
  lw $a1, 4($sp)
  lw $a2, 8($sp)
  addi $sp, $sp, 12
end_quicksort:
  lw $ra, 60($sp)
  lw $fp, 56($sp)
  addi $sp, $sp, 64
  jr $ra

